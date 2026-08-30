package com.yuukias.seminararc.domain

import com.yuukias.seminararc.data.storage.ClipOutputFile
import com.yuukias.seminararc.data.storage.PhotoOutputFile
import com.yuukias.seminararc.data.storage.RecordingOutputFile
import com.yuukias.seminararc.data.storage.StoredFile
import com.yuukias.seminararc.data.storage.MediaStorageManager
import com.yuukias.seminararc.domain.model.AssetTag
import com.yuukias.seminararc.domain.model.OcrResult
import com.yuukias.seminararc.domain.model.ProcessingJob
import com.yuukias.seminararc.domain.model.ProcessingJobState
import com.yuukias.seminararc.domain.model.ProcessingJobType
import com.yuukias.seminararc.domain.model.SeminarAsset
import com.yuukias.seminararc.domain.model.SeminarAssetType
import com.yuukias.seminararc.domain.model.SeminarSystemTag
import com.yuukias.seminararc.domain.ocr.TextOcrLanguageMode
import com.yuukias.seminararc.domain.ocr.TextOcrProvider
import com.yuukias.seminararc.domain.ocr.TextOcrRecognition
import com.yuukias.seminararc.domain.ocr.TextOcrResult
import com.yuukias.seminararc.domain.repository.CreateDerivedAssetInput
import com.yuukias.seminararc.domain.repository.EnqueueProcessingJobInput
import com.yuukias.seminararc.domain.repository.ReconstructionRepository
import com.yuukias.seminararc.domain.repository.SaveOcrResultInput
import com.yuukias.seminararc.domain.usecase.RunTextOcrForAssetUseCase
import com.yuukias.seminararc.domain.usecase.RunTextOcrResult
import java.io.File
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RunTextOcrForAssetUseCaseTest {
    @get:Rule
    val temporaryFolder: TemporaryFolder = TemporaryFolder()

    @Test
    fun runTextOcrSavesResultAndMarksJobSucceeded() = runTest {
        val source = temporaryFolder.newFile("source.jpg")
        val repository = FakeOcrReconstructionRepository()
        val asset = repository.addPhotoAsset()
        val storage = FakeOcrStorage(source)
        val provider = FakeTextOcrProvider(
            TextOcrResult.Recognized(
                TextOcrRecognition(
                    recognizedText = "Sparse recovery",
                    blockJson = """[{"text":"Sparse recovery"}]""",
                    languageHint = TextOcrLanguageMode.LATIN_AND_CHINESE.name,
                    confidence = null,
                ),
            ),
        )
        val useCase = RunTextOcrForAssetUseCase(repository, storage, provider)

        val result = useCase(asset.id) as RunTextOcrResult.Recognized

        assertEquals("Sparse recovery", result.ocrResult.recognizedText)
        assertEquals(asset.id, result.ocrResult.assetId)
        assertEquals(ProcessingJobState.SUCCEEDED, repository.jobs.single().state)
        assertEquals(asset.id, repository.jobs.single().outputAssetId)
        assertEquals(1, provider.invocationCount)
    }

    @Test
    fun runTextOcrFailureMarksRetryableJob() = runTest {
        val source = temporaryFolder.newFile("source.jpg")
        val repository = FakeOcrReconstructionRepository()
        val asset = repository.addPhotoAsset()
        val storage = FakeOcrStorage(source)
        val provider = FakeTextOcrProvider(TextOcrResult.Failed("OCR failed", isRetryable = true))
        val useCase = RunTextOcrForAssetUseCase(repository, storage, provider)

        val result = useCase(asset.id)

        assertEquals(RunTextOcrResult.Failed("OCR failed"), result)
        assertEquals(ProcessingJobState.FAILED, repository.jobs.single().state)
        assertTrue(repository.jobs.single().isRetryable)
        assertEquals("OCR failed", repository.jobs.single().errorMessage)
    }
}

private class FakeOcrStorage(
    private val source: File,
) : MediaStorageManager {
    override suspend fun importAbstractPdf(seminarId: Long, sourceUri: String): StoredFile {
        error("PDF import is not used in this test.")
    }

    override suspend fun createRecordingOutputFile(seminarId: Long, startedAt: Instant): RecordingOutputFile {
        error("Recording output is not used in this test.")
    }

    override suspend fun createPhotoOutputFile(seminarId: Long, capturedAt: Instant): PhotoOutputFile {
        error("Photo output is not used in this test.")
    }

    override suspend fun createClipOutputFile(seminarId: Long, clipId: Long): ClipOutputFile {
        error("Clip output is not used in this test.")
    }

    override suspend fun resolveReadableRelativeFile(relativePath: String): File? = source

    override suspend fun deleteRelativeFile(relativePath: String) = Unit

    override suspend fun deleteSeminarMedia(seminarId: Long) = Unit
}

private class FakeTextOcrProvider(
    private val result: TextOcrResult,
) : TextOcrProvider {
    override val providerId: String = "fake-text-ocr"
    override val providerVersion: String = "1"
    var invocationCount = 0
        private set

    override suspend fun recognize(source: File, languageMode: TextOcrLanguageMode): TextOcrResult {
        invocationCount += 1
        return result
    }
}

private class FakeOcrReconstructionRepository : ReconstructionRepository {
    private val assets = mutableListOf<SeminarAsset>()
    private val ocrResults = mutableListOf<OcrResult>()
    val jobs = mutableListOf<ProcessingJob>()
    private var nextJobId = 1L
    private var nextOcrResultId = 1L

    fun addPhotoAsset(): SeminarAsset {
        val asset = SeminarAsset(
            id = 1L,
            seminarId = 1L,
            type = SeminarAssetType.PHOTO_ORIGINAL,
            originAssetId = null,
            sourceTimelineEventId = null,
            sourceRecordingId = null,
            sourceClipId = null,
            relativePath = "seminars/1/photos/source.jpg",
            mimeType = "image/jpeg",
            displayName = "source.jpg",
            createdAt = NOW,
            updatedAt = NOW,
        )
        assets += asset
        return asset
    }

    override fun observeAssetsForSeminar(seminarId: Long): Flow<List<SeminarAsset>> = flowOf(assets)

    override fun observePhotoAssetsForSeminar(seminarId: Long): Flow<List<SeminarAsset>> = flowOf(assets)

    override fun observeJobsForSeminar(seminarId: Long): Flow<List<ProcessingJob>> = flowOf(jobs)

    override fun observeOcrResultsForSeminar(seminarId: Long): Flow<List<OcrResult>> = flowOf(ocrResults)

    override fun observeTagsForAsset(assetId: Long): Flow<List<AssetTag>> = flowOf(emptyList())

    override fun observeAssetIdsForSystemTag(seminarId: Long, tag: SeminarSystemTag): Flow<List<Long>> = flowOf(emptyList())

    override suspend fun getAsset(assetId: Long): SeminarAsset? = assets.firstOrNull { it.id == assetId }

    override suspend fun getAssetByRelativePath(relativePath: String): SeminarAsset? = null

    override suspend fun createDerivedAsset(input: CreateDerivedAssetInput): SeminarAsset {
        error("Derived assets are not used in this test.")
    }

    override suspend fun enqueueJob(input: EnqueueProcessingJobInput): ProcessingJob {
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
        ocrResults.removeAll { it.assetId == input.assetId }
        val result = OcrResult(
            id = nextOcrResultId++,
            seminarId = input.seminarId,
            assetId = input.assetId,
            recognizedText = input.recognizedText,
            editedText = input.editedText,
            blockJson = input.blockJson,
            languageHint = input.languageHint,
            confidence = input.confidence,
            providerId = input.providerId,
            providerVersion = input.providerVersion,
            isEdited = input.editedText != null,
            createdAt = NOW,
            updatedAt = NOW,
        )
        ocrResults += result
        return result
    }

    override suspend fun editOcrResult(assetId: Long, editedText: String): Boolean = false

    override suspend fun setSystemTag(assetId: Long, tag: SeminarSystemTag, enabled: Boolean) = Unit

    private fun replaceJob(jobId: Long, transform: (ProcessingJob) -> ProcessingJob) {
        val index = jobs.indexOfFirst { it.id == jobId }
        check(index >= 0) { "Job $jobId not found." }
        jobs[index] = transform(jobs[index])
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-08-30T10:00:00Z")
    }
}
