package com.yuukias.seminararc.domain

import com.yuukias.seminararc.data.storage.EnhancedPhotoOutputFile
import com.yuukias.seminararc.data.storage.ClipOutputFile
import com.yuukias.seminararc.data.storage.MediaStorageManager
import com.yuukias.seminararc.data.storage.PhotoOutputFile
import com.yuukias.seminararc.data.storage.RecordingOutputFile
import com.yuukias.seminararc.data.storage.StoredFile
import com.yuukias.seminararc.domain.image.ImageEnhancementOptions
import com.yuukias.seminararc.domain.image.ImageEnhancementProvider
import com.yuukias.seminararc.domain.image.ImageEnhancementResult
import com.yuukias.seminararc.domain.model.AssetTag
import com.yuukias.seminararc.domain.model.OcrResult
import com.yuukias.seminararc.domain.model.ProcessingJob
import com.yuukias.seminararc.domain.model.ProcessingJobState
import com.yuukias.seminararc.domain.model.ProcessingJobType
import com.yuukias.seminararc.domain.model.SeminarAsset
import com.yuukias.seminararc.domain.model.SeminarAssetType
import com.yuukias.seminararc.domain.model.SeminarSystemTag
import com.yuukias.seminararc.domain.repository.CreateDerivedAssetInput
import com.yuukias.seminararc.domain.repository.EnqueueProcessingJobInput
import com.yuukias.seminararc.domain.repository.ReconstructionRepository
import com.yuukias.seminararc.domain.repository.SaveOcrResultInput
import com.yuukias.seminararc.domain.usecase.EnhancePhotoAssetResult
import com.yuukias.seminararc.domain.usecase.EnhancePhotoAssetUseCase
import java.io.File
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EnhancePhotoAssetUseCaseTest {
    @get:Rule
    val temporaryFolder: TemporaryFolder = TemporaryFolder()

    @Test
    fun enhancePhotoAssetCreatesDerivedAssetAndMarksJobSucceeded() = runTest {
        val source = temporaryFolder.newFile("source.jpg").apply { writeText("original") }
        val repository = FakeReconstructionRepository()
        val origin = repository.addAsset(relativePath = "seminars/1/photos/source.jpg")
        val storage = FakeMediaStorageManager(temporaryFolder.root, source)
        val provider = FakeImageEnhancementProvider()
        val useCase = EnhancePhotoAssetUseCase(repository, storage, provider)

        val result = useCase(origin.id) as EnhancePhotoAssetResult.Enhanced

        assertEquals(SeminarAssetType.PHOTO_ENHANCED, result.asset.type)
        assertEquals(origin.id, result.asset.originAssetId)
        assertEquals(ProcessingJobState.SUCCEEDED, repository.jobs.single().state)
        assertEquals(result.asset.id, repository.jobs.single().outputAssetId)
        assertEquals("original", source.readText())
        assertEquals("enhanced", File(temporaryFolder.root, result.asset.relativePath!!).readText())
    }

    @Test
    fun enhancePhotoAssetReturnsExistingDerivedAssetForSameVariant() = runTest {
        val source = temporaryFolder.newFile("source.jpg").apply { writeText("original") }
        val repository = FakeReconstructionRepository()
        val origin = repository.addAsset(relativePath = "seminars/1/photos/source.jpg")
        val storage = FakeMediaStorageManager(temporaryFolder.root, source)
        val provider = FakeImageEnhancementProvider()
        val useCase = EnhancePhotoAssetUseCase(repository, storage, provider)

        useCase(origin.id)
        val result = useCase(origin.id)

        assertTrue(result is EnhancePhotoAssetResult.AlreadyEnhanced)
        assertEquals(1, provider.invocationCount)
        assertEquals(1, repository.jobs.size)
    }

    @Test
    fun enhancePhotoAssetFailureMarksRetryableJobAndDeletesOutput() = runTest {
        val source = temporaryFolder.newFile("source.jpg").apply { writeText("original") }
        val repository = FakeReconstructionRepository()
        val origin = repository.addAsset(relativePath = "seminars/1/photos/source.jpg")
        val storage = FakeMediaStorageManager(temporaryFolder.root, source)
        val provider = FakeImageEnhancementProvider(failureMessage = "decode failed")
        val useCase = EnhancePhotoAssetUseCase(repository, storage, provider)

        val result = useCase(origin.id)

        assertEquals(EnhancePhotoAssetResult.Failed("decode failed"), result)
        assertEquals(ProcessingJobState.FAILED, repository.jobs.single().state)
        assertTrue(repository.jobs.single().isRetryable)
        assertFalse(File(temporaryFolder.root, "seminars/1/enhanced/enhanced-photo-1-r0-crop-none-persp-none-read-standard-q92.jpg").exists())
    }
}

private class FakeMediaStorageManager(
    private val root: File,
    private val source: File,
) : MediaStorageManager {
    override suspend fun importAbstractPdf(seminarId: Long, sourceUri: String): StoredFile {
        error("PDF import is not used in this test.")
    }

    override suspend fun createRecordingOutputFile(seminarId: Long, startedAt: Instant): RecordingOutputFile {
        error("Recording output is not used in this test.")
    }

    override suspend fun createPhotoOutputFile(seminarId: Long, capturedAt: Instant): PhotoOutputFile {
        error("Photo capture output is not used in this test.")
    }

    override suspend fun createEnhancedPhotoOutputFile(
        seminarId: Long,
        originAssetId: Long,
        variantKey: String,
    ): EnhancedPhotoOutputFile {
        val relativePath = "seminars/$seminarId/enhanced/enhanced-photo-$originAssetId-$variantKey.jpg"
        return EnhancedPhotoOutputFile(
            displayName = "enhanced-photo-$originAssetId-$variantKey.jpg",
            relativePath = relativePath,
            file = File(root, relativePath),
        )
    }

    override suspend fun createClipOutputFile(seminarId: Long, clipId: Long): ClipOutputFile {
        error("Clip output is not used in this test.")
    }

    override suspend fun resolveReadableRelativeFile(relativePath: String): File? = source

    override suspend fun deleteRelativeFile(relativePath: String) {
        File(root, relativePath).delete()
    }

    override suspend fun deleteSeminarMedia(seminarId: Long) = Unit
}

private class FakeImageEnhancementProvider(
    private val failureMessage: String? = null,
) : ImageEnhancementProvider {
    override val providerId: String = "fake-image-enhancement"
    override val providerVersion: String = "1"
    var invocationCount = 0
        private set

    override suspend fun enhance(
        source: File,
        output: File,
        options: ImageEnhancementOptions,
    ): ImageEnhancementResult {
        invocationCount += 1
        failureMessage?.let { message ->
            output.parentFile?.mkdirs()
            output.writeText("partial")
            return ImageEnhancementResult.Failed(message)
        }
        output.parentFile?.mkdirs()
        output.writeText("enhanced")
        return ImageEnhancementResult.Enhanced(output, width = 10, height = 10, mimeType = "image/jpeg")
    }
}

private class FakeReconstructionRepository : ReconstructionRepository {
    private val assets = mutableListOf<SeminarAsset>()
    val jobs = mutableListOf<ProcessingJob>()
    private var nextAssetId = 1L
    private var nextJobId = 1L

    fun addAsset(relativePath: String): SeminarAsset {
        val asset = asset(
            id = nextAssetId++,
            type = SeminarAssetType.PHOTO_ORIGINAL,
            originAssetId = null,
            relativePath = relativePath,
        )
        assets += asset
        return asset
    }

    override fun observeAssetsForSeminar(seminarId: Long): Flow<List<SeminarAsset>> = flowOf(assets)

    override fun observePhotoAssetsForSeminar(seminarId: Long): Flow<List<SeminarAsset>> = flowOf(assets)

    override fun observeJobsForSeminar(seminarId: Long): Flow<List<ProcessingJob>> = flowOf(jobs)

    override fun observeOcrResultsForSeminar(seminarId: Long): Flow<List<OcrResult>> = flowOf(emptyList())

    override fun observeTagsForAsset(assetId: Long): Flow<List<AssetTag>> = flowOf(emptyList())

    override fun observeAssetIdsForSystemTag(seminarId: Long, tag: SeminarSystemTag): Flow<List<Long>> = flowOf(emptyList())

    override suspend fun getAsset(assetId: Long): SeminarAsset? {
        return assets.firstOrNull { it.id == assetId }
    }

    override suspend fun getAssetByRelativePath(relativePath: String): SeminarAsset? {
        return assets.firstOrNull { it.relativePath == relativePath }
    }

    override suspend fun createDerivedAsset(input: CreateDerivedAssetInput): SeminarAsset {
        val asset = asset(
            id = nextAssetId++,
            type = input.type,
            originAssetId = input.originAssetId,
            relativePath = input.relativePath,
            displayName = input.displayName,
        )
        assets += asset
        return asset
    }

    override suspend fun enqueueJob(input: EnqueueProcessingJobInput): ProcessingJob {
        jobs.firstOrNull {
            it.inputAssetId == input.inputAssetId &&
                it.type == input.type &&
                it.state in listOf(ProcessingJobState.QUEUED, ProcessingJobState.RUNNING)
        }?.let { existing -> return existing }
        val job = ProcessingJob(
            id = nextJobId++,
            seminarId = input.seminarId,
            type = input.type,
            state = ProcessingJobState.QUEUED,
            inputAssetId = input.inputAssetId,
            outputAssetId = null,
            providerId = input.providerId,
            providerVersion = input.providerVersion,
            createdAt = NOW,
            startedAt = null,
            completedAt = null,
            retryCount = 0,
            isRetryable = true,
            errorMessage = null,
        )
        jobs += job
        return job
    }

    override suspend fun markJobRunning(jobId: Long) {
        replaceJob(jobId) { job -> job.copy(state = ProcessingJobState.RUNNING, startedAt = NOW) }
    }

    override suspend fun markJobSucceeded(jobId: Long, outputAssetId: Long?) {
        replaceJob(jobId) { job ->
            job.copy(
                state = ProcessingJobState.SUCCEEDED,
                outputAssetId = outputAssetId,
                completedAt = NOW,
                isRetryable = false,
            )
        }
    }

    override suspend fun markJobFailed(jobId: Long, message: String, isRetryable: Boolean) {
        replaceJob(jobId) { job ->
            job.copy(
                state = ProcessingJobState.FAILED,
                completedAt = NOW,
                retryCount = job.retryCount + 1,
                isRetryable = isRetryable,
                errorMessage = message,
            )
        }
    }

    override suspend fun markJobCancelled(jobId: Long) {
        replaceJob(jobId) { job -> job.copy(state = ProcessingJobState.CANCELLED, completedAt = NOW) }
    }

    override suspend fun saveOcrResult(input: SaveOcrResultInput): OcrResult {
        error("OCR is not used in this test.")
    }

    override suspend fun editOcrResult(assetId: Long, editedText: String): Boolean = false

    override suspend fun setSystemTag(assetId: Long, tag: SeminarSystemTag, enabled: Boolean) = Unit

    private fun replaceJob(jobId: Long, transform: (ProcessingJob) -> ProcessingJob) {
        val index = jobs.indexOfFirst { it.id == jobId }
        check(index >= 0) { "Job $jobId not found." }
        jobs[index] = transform(jobs[index])
    }

    private fun asset(
        id: Long,
        type: SeminarAssetType,
        originAssetId: Long?,
        relativePath: String,
        displayName: String? = null,
    ): SeminarAsset {
        return SeminarAsset(
            id = id,
            seminarId = 1L,
            type = type,
            originAssetId = originAssetId,
            sourceTimelineEventId = null,
            sourceRecordingId = null,
            sourceClipId = null,
            relativePath = relativePath,
            mimeType = "image/jpeg",
            displayName = displayName,
            createdAt = NOW,
            updatedAt = NOW,
        )
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-08-30T09:00:00Z")
    }
}
