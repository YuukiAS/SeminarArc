package com.yuukias.seminararc.media.processing

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.yuukias.seminararc.data.local.AppDatabase
import com.yuukias.seminararc.data.storage.MediaStorageManager
import com.yuukias.seminararc.domain.image.FractionalCrop
import com.yuukias.seminararc.domain.image.FractionalPerspective
import com.yuukias.seminararc.domain.image.FractionalPoint
import com.yuukias.seminararc.domain.image.ImageEnhancementOptions
import com.yuukias.seminararc.domain.image.ImageEnhancementProvider
import com.yuukias.seminararc.domain.image.ImageEnhancementResult
import com.yuukias.seminararc.domain.image.ReadabilityEnhancement
import com.yuukias.seminararc.domain.model.ProcessingJobState
import com.yuukias.seminararc.domain.model.ProcessingJobType
import com.yuukias.seminararc.domain.model.SeminarAssetType
import com.yuukias.seminararc.domain.ocr.TextOcrLanguageMode
import com.yuukias.seminararc.domain.ocr.TextOcrProvider
import com.yuukias.seminararc.domain.ocr.TextOcrResult
import com.yuukias.seminararc.domain.repository.CreateDerivedAssetInput
import com.yuukias.seminararc.domain.repository.ReconstructionRepository
import com.yuukias.seminararc.domain.repository.SaveOcrResultInput
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException

class ProcessingWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val jobId = inputData.getLong(KEY_JOB_ID, -1L)
        if (jobId <= 0L) return Result.failure()
        val dependencies = EntryPointAccessors.fromApplication(
            applicationContext,
            ProcessingWorkerEntryPoint::class.java,
        )
        val repository = dependencies.reconstructionRepository()
        val job = repository.getJob(jobId) ?: return Result.failure()
        if (job.state == ProcessingJobState.CANCELLED) return Result.failure()
        if (job.state == ProcessingJobState.SUCCEEDED) return Result.success()
        return try {
            when (job.type) {
                ProcessingJobType.IMAGE_ENHANCEMENT -> runImageEnhancement(
                    jobId = job.id,
                    repository = repository,
                    storage = dependencies.mediaStorageManager(),
                    provider = dependencies.imageEnhancementProvider(),
                )
                ProcessingJobType.TEXT_OCR -> runTextOcr(
                    jobId = job.id,
                    repository = repository,
                    storage = dependencies.mediaStorageManager(),
                    provider = dependencies.textOcrProvider(),
                )
            }
        } catch (cancellation: CancellationException) {
            repository.markJobCancelled(jobId)
            Result.failure()
        } catch (throwable: Throwable) {
            repository.markJobFailed(jobId, throwable.message ?: "Processing failed.", isRetryable = true)
            Result.failure()
        }
    }

    private suspend fun runImageEnhancement(
        jobId: Long,
        repository: ReconstructionRepository,
        storage: MediaStorageManager,
        provider: ImageEnhancementProvider,
    ): Result {
        val job = repository.getJob(jobId) ?: return Result.failure()
        val origin = repository.getAsset(job.inputAssetId)
            ?: return fail(repository, jobId, "Source asset was not found.", isRetryable = false)
        if (origin.type != SeminarAssetType.PHOTO_ORIGINAL && origin.type != SeminarAssetType.PHOTO_ENHANCED) {
            return fail(repository, jobId, "Only photo assets can be enhanced.", isRetryable = false)
        }
        val sourcePath = origin.relativePath
            ?: return fail(repository, jobId, "Source asset has no local file path.", isRetryable = false)
        val source = storage.resolveReadableRelativeFile(sourcePath)
            ?: return fail(repository, jobId, "Source photo file is not readable.", isRetryable = true)
        val options = imageOptionsFromInput()
        val output = storage.createEnhancedPhotoOutputFile(
            seminarId = origin.seminarId,
            originAssetId = origin.id,
            variantKey = options.variantKey(),
        )
        repository.getAssetByRelativePath(output.relativePath)?.let { existing ->
            repository.markJobSucceeded(jobId, existing.id)
            return Result.success()
        }
        repository.markJobRunning(jobId)
        return when (val result = provider.enhance(source, output.file, options)) {
            is ImageEnhancementResult.Enhanced -> {
                val asset = repository.createDerivedAsset(
                    CreateDerivedAssetInput(
                        seminarId = origin.seminarId,
                        type = SeminarAssetType.PHOTO_ENHANCED,
                        originAssetId = origin.id,
                        relativePath = output.relativePath,
                        mimeType = result.mimeType,
                        displayName = output.displayName,
                    ),
                )
                repository.markJobSucceeded(jobId, asset.id)
                Result.success()
            }
            is ImageEnhancementResult.Failed -> {
                storage.deleteRelativeFile(output.relativePath)
                fail(repository, jobId, result.message, isRetryable = true)
            }
        }
    }

    private suspend fun runTextOcr(
        jobId: Long,
        repository: ReconstructionRepository,
        storage: MediaStorageManager,
        provider: TextOcrProvider,
    ): Result {
        val job = repository.getJob(jobId) ?: return Result.failure()
        val asset = repository.getAsset(job.inputAssetId)
            ?: return fail(repository, jobId, "Source asset was not found.", isRetryable = false)
        if (asset.type != SeminarAssetType.PHOTO_ORIGINAL && asset.type != SeminarAssetType.PHOTO_ENHANCED) {
            return fail(repository, jobId, "Only photo assets can be OCR processed.", isRetryable = false)
        }
        val relativePath = asset.relativePath
            ?: return fail(repository, jobId, "Source asset has no local file path.", isRetryable = false)
        val source = storage.resolveReadableRelativeFile(relativePath)
            ?: return fail(repository, jobId, "Source image file is not readable.", isRetryable = true)
        repository.markJobRunning(jobId)
        return when (val ocr = provider.recognize(source, languageModeFromInput())) {
            is TextOcrResult.Recognized -> {
                repository.saveOcrResult(
                    SaveOcrResultInput(
                        seminarId = asset.seminarId,
                        assetId = asset.id,
                        recognizedText = ocr.recognition.recognizedText,
                        editedText = null,
                        blockJson = ocr.recognition.blockJson,
                        languageHint = ocr.recognition.languageHint,
                        confidence = ocr.recognition.confidence,
                        providerId = provider.providerId,
                        providerVersion = provider.providerVersion,
                    ),
                )
                repository.markJobSucceeded(jobId, outputAssetId = asset.id)
                Result.success()
            }
            is TextOcrResult.Failed -> fail(repository, jobId, ocr.message, isRetryable = ocr.isRetryable)
        }
    }

    private suspend fun fail(
        repository: ReconstructionRepository,
        jobId: Long,
        message: String,
        isRetryable: Boolean,
    ): Result {
        repository.markJobFailed(jobId, message, isRetryable)
        return Result.failure()
    }

    private fun imageOptionsFromInput(): ImageEnhancementOptions {
        return ImageEnhancementOptions(
            rotationDegrees = inputData.getInt(KEY_ROTATION_DEGREES, 0),
            crop = cropFromInput(),
            perspective = perspectiveFromInput(),
            readability = enumValueOrDefault(
                inputData.getString(KEY_READABILITY),
                ReadabilityEnhancement.STANDARD,
            ),
            jpegQuality = inputData.getInt(KEY_JPEG_QUALITY, ImageEnhancementOptions.DEFAULT_JPEG_QUALITY),
        )
    }

    private fun cropFromInput(): FractionalCrop? {
        if (!inputData.getBoolean(KEY_HAS_CROP, false)) return null
        return FractionalCrop(
            left = inputData.getFloat(KEY_CROP_LEFT, 0f),
            top = inputData.getFloat(KEY_CROP_TOP, 0f),
            right = inputData.getFloat(KEY_CROP_RIGHT, 1f),
            bottom = inputData.getFloat(KEY_CROP_BOTTOM, 1f),
        )
    }

    private fun perspectiveFromInput(): FractionalPerspective? {
        if (!inputData.getBoolean(KEY_HAS_PERSPECTIVE, false)) return null
        return FractionalPerspective(
            topLeft = point(KEY_PERSPECTIVE_TOP_LEFT_X, KEY_PERSPECTIVE_TOP_LEFT_Y),
            topRight = point(KEY_PERSPECTIVE_TOP_RIGHT_X, KEY_PERSPECTIVE_TOP_RIGHT_Y),
            bottomRight = point(KEY_PERSPECTIVE_BOTTOM_RIGHT_X, KEY_PERSPECTIVE_BOTTOM_RIGHT_Y),
            bottomLeft = point(KEY_PERSPECTIVE_BOTTOM_LEFT_X, KEY_PERSPECTIVE_BOTTOM_LEFT_Y),
        )
    }

    private fun point(xKey: String, yKey: String): FractionalPoint {
        return FractionalPoint(
            x = inputData.getFloat(xKey, 0f),
            y = inputData.getFloat(yKey, 0f),
        )
    }

    private fun languageModeFromInput(): TextOcrLanguageMode {
        return enumValueOrDefault(
            inputData.getString(KEY_LANGUAGE_MODE),
            TextOcrLanguageMode.LATIN_AND_CHINESE,
        )
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(
        value: String?,
        default: T,
    ): T = value?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default

    companion object {
        const val WORK_TAG = "seminararc-processing"
        const val KEY_JOB_ID = "job_id"
        const val KEY_OPERATION = "operation"
        const val KEY_LANGUAGE_MODE = "language_mode"
        const val KEY_ROTATION_DEGREES = "rotation_degrees"
        const val KEY_READABILITY = "readability"
        const val KEY_JPEG_QUALITY = "jpeg_quality"
        const val KEY_HAS_CROP = "has_crop"
        const val KEY_CROP_LEFT = "crop_left"
        const val KEY_CROP_TOP = "crop_top"
        const val KEY_CROP_RIGHT = "crop_right"
        const val KEY_CROP_BOTTOM = "crop_bottom"
        const val KEY_HAS_PERSPECTIVE = "has_perspective"
        const val KEY_PERSPECTIVE_TOP_LEFT_X = "perspective_top_left_x"
        const val KEY_PERSPECTIVE_TOP_LEFT_Y = "perspective_top_left_y"
        const val KEY_PERSPECTIVE_TOP_RIGHT_X = "perspective_top_right_x"
        const val KEY_PERSPECTIVE_TOP_RIGHT_Y = "perspective_top_right_y"
        const val KEY_PERSPECTIVE_BOTTOM_RIGHT_X = "perspective_bottom_right_x"
        const val KEY_PERSPECTIVE_BOTTOM_RIGHT_Y = "perspective_bottom_right_y"
        const val KEY_PERSPECTIVE_BOTTOM_LEFT_X = "perspective_bottom_left_x"
        const val KEY_PERSPECTIVE_BOTTOM_LEFT_Y = "perspective_bottom_left_y"
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ProcessingWorkerEntryPoint {
    fun appDatabase(): AppDatabase
    fun reconstructionRepository(): ReconstructionRepository
    fun mediaStorageManager(): MediaStorageManager
    fun imageEnhancementProvider(): ImageEnhancementProvider
    fun textOcrProvider(): TextOcrProvider
    fun processingWorkScheduler(): ProcessingWorkScheduler
}
