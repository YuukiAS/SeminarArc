package com.yuukias.seminararc.media.processing

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.yuukias.seminararc.domain.image.ImageEnhancementOptions
import com.yuukias.seminararc.domain.model.ProcessingJob
import com.yuukias.seminararc.domain.model.ProcessingJobType
import com.yuukias.seminararc.domain.ocr.TextOcrLanguageMode
import com.yuukias.seminararc.domain.repository.EnqueueProcessingJobInput
import com.yuukias.seminararc.domain.repository.ReconstructionRepository
import com.yuukias.seminararc.domain.image.ImageEnhancementProvider
import com.yuukias.seminararc.domain.ocr.TextOcrProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WorkManagerProcessingWorkScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val reconstructionRepository: ReconstructionRepository,
    private val imageEnhancementProvider: ImageEnhancementProvider,
    private val textOcrProvider: TextOcrProvider,
) : ProcessingWorkScheduler {
    private val workManager: WorkManager
        get() = WorkManager.getInstance(context)
    private val recoveryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override suspend fun enqueueImageEnhancement(
        assetId: Long,
        options: ImageEnhancementOptions,
    ): ProcessingJob? {
        val asset = reconstructionRepository.getAsset(assetId) ?: return null
        val job = reconstructionRepository.enqueueJob(
            EnqueueProcessingJobInput(
                seminarId = asset.seminarId,
                type = ProcessingJobType.IMAGE_ENHANCEMENT,
                inputAssetId = asset.id,
                providerId = imageEnhancementProvider.providerId,
                providerVersion = imageEnhancementProvider.providerVersion,
            ),
        )
        enqueue(job, imageEnhancementRequest(job.id, options), ExistingWorkPolicy.KEEP)
        return job
    }

    override suspend fun enqueueTextOcr(
        assetId: Long,
        languageMode: TextOcrLanguageMode,
    ): ProcessingJob? {
        val asset = reconstructionRepository.getAsset(assetId) ?: return null
        val job = reconstructionRepository.enqueueJob(
            EnqueueProcessingJobInput(
                seminarId = asset.seminarId,
                type = ProcessingJobType.TEXT_OCR,
                inputAssetId = asset.id,
                providerId = textOcrProvider.providerId,
                providerVersion = textOcrProvider.providerVersion,
            ),
        )
        enqueue(job, textOcrRequest(job.id, languageMode), ExistingWorkPolicy.KEEP)
        return job
    }

    override suspend fun retry(jobId: Long): ProcessingJob? {
        val job = reconstructionRepository.requeueJob(jobId) ?: return null
        enqueue(job, requestFor(job), ExistingWorkPolicy.REPLACE)
        return job
    }

    override suspend fun cancel(jobId: Long) {
        workManager.cancelUniqueWork(workName(jobId))
        reconstructionRepository.markJobCancelled(jobId)
    }

    override fun recoverProcessingJobs() {
        recoveryScope.launch {
            reconstructionRepository.recoverInterruptedJobs().forEach { job ->
                enqueue(job, requestFor(job), ExistingWorkPolicy.KEEP)
            }
        }
    }

    private fun enqueue(
        job: ProcessingJob,
        request: OneTimeWorkRequest,
        policy: ExistingWorkPolicy,
    ) {
        workManager.enqueueUniqueWork(workName(job.id), policy, request)
    }

    private fun requestFor(job: ProcessingJob): OneTimeWorkRequest {
        return when (job.type) {
            ProcessingJobType.IMAGE_ENHANCEMENT -> imageEnhancementRequest(job.id, ImageEnhancementOptions())
            ProcessingJobType.TEXT_OCR -> textOcrRequest(job.id, TextOcrLanguageMode.LATIN_AND_CHINESE)
        }
    }

    private fun imageEnhancementRequest(
        jobId: Long,
        options: ImageEnhancementOptions,
    ): OneTimeWorkRequest {
        return OneTimeWorkRequestBuilder<ProcessingWorker>()
            .setInputData(
                workDataOf(
                    ProcessingWorker.KEY_JOB_ID to jobId,
                    ProcessingWorker.KEY_OPERATION to ProcessingJobType.IMAGE_ENHANCEMENT.name,
                    ProcessingWorker.KEY_ROTATION_DEGREES to options.rotationDegrees,
                    ProcessingWorker.KEY_READABILITY to options.readability.name,
                    ProcessingWorker.KEY_JPEG_QUALITY to options.jpegQuality,
                    ProcessingWorker.KEY_HAS_CROP to (options.crop != null),
                    ProcessingWorker.KEY_CROP_LEFT to (options.crop?.left ?: 0f),
                    ProcessingWorker.KEY_CROP_TOP to (options.crop?.top ?: 0f),
                    ProcessingWorker.KEY_CROP_RIGHT to (options.crop?.right ?: 1f),
                    ProcessingWorker.KEY_CROP_BOTTOM to (options.crop?.bottom ?: 1f),
                    ProcessingWorker.KEY_HAS_PERSPECTIVE to (options.perspective != null),
                    ProcessingWorker.KEY_PERSPECTIVE_TOP_LEFT_X to (options.perspective?.topLeft?.x ?: 0f),
                    ProcessingWorker.KEY_PERSPECTIVE_TOP_LEFT_Y to (options.perspective?.topLeft?.y ?: 0f),
                    ProcessingWorker.KEY_PERSPECTIVE_TOP_RIGHT_X to (options.perspective?.topRight?.x ?: 1f),
                    ProcessingWorker.KEY_PERSPECTIVE_TOP_RIGHT_Y to (options.perspective?.topRight?.y ?: 0f),
                    ProcessingWorker.KEY_PERSPECTIVE_BOTTOM_RIGHT_X to (options.perspective?.bottomRight?.x ?: 1f),
                    ProcessingWorker.KEY_PERSPECTIVE_BOTTOM_RIGHT_Y to (options.perspective?.bottomRight?.y ?: 1f),
                    ProcessingWorker.KEY_PERSPECTIVE_BOTTOM_LEFT_X to (options.perspective?.bottomLeft?.x ?: 0f),
                    ProcessingWorker.KEY_PERSPECTIVE_BOTTOM_LEFT_Y to (options.perspective?.bottomLeft?.y ?: 1f),
                ),
            )
            .addTag(ProcessingWorker.WORK_TAG)
            .build()
    }

    private fun textOcrRequest(
        jobId: Long,
        languageMode: TextOcrLanguageMode,
    ): OneTimeWorkRequest {
        return OneTimeWorkRequestBuilder<ProcessingWorker>()
            .setInputData(
                workDataOf(
                    ProcessingWorker.KEY_JOB_ID to jobId,
                    ProcessingWorker.KEY_OPERATION to ProcessingJobType.TEXT_OCR.name,
                    ProcessingWorker.KEY_LANGUAGE_MODE to languageMode.name,
                ),
            )
            .addTag(ProcessingWorker.WORK_TAG)
            .build()
    }

    private companion object {
        fun workName(jobId: Long): String = "seminararc-processing-$jobId"
    }
}
