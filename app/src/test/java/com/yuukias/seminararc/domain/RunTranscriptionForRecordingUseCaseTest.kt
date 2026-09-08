package com.yuukias.seminararc.domain

import com.yuukias.seminararc.data.storage.ClipOutputFile
import com.yuukias.seminararc.data.storage.MediaStorageManager
import com.yuukias.seminararc.data.storage.PhotoOutputFile
import com.yuukias.seminararc.data.storage.RecordingOutputFile
import com.yuukias.seminararc.data.storage.StoredFile
import com.yuukias.seminararc.domain.model.AssetTag
import com.yuukias.seminararc.domain.model.BeginRecordingResult
import com.yuukias.seminararc.domain.model.CompleteRecordingResult
import com.yuukias.seminararc.domain.model.FailRecordingResult
import com.yuukias.seminararc.domain.model.OcrResult
import com.yuukias.seminararc.domain.model.ProcessingJob
import com.yuukias.seminararc.domain.model.ProcessingJobState
import com.yuukias.seminararc.domain.model.ProcessingJobType
import com.yuukias.seminararc.domain.model.RecordingSession
import com.yuukias.seminararc.domain.model.RecordingState
import com.yuukias.seminararc.domain.model.SeminarAsset
import com.yuukias.seminararc.domain.model.SeminarAssetType
import com.yuukias.seminararc.domain.model.SeminarSystemTag
import com.yuukias.seminararc.domain.model.SummaryDraft
import com.yuukias.seminararc.domain.model.Transcript
import com.yuukias.seminararc.domain.model.TranscriptLanguageHint
import com.yuukias.seminararc.domain.model.TranscriptSegment
import com.yuukias.seminararc.domain.model.TranscriptSourceType
import com.yuukias.seminararc.domain.model.TranscriptState
import com.yuukias.seminararc.domain.repository.CreateDerivedAssetInput
import com.yuukias.seminararc.domain.repository.CreateTranscriptInput
import com.yuukias.seminararc.domain.repository.EnqueueProcessingJobInput
import com.yuukias.seminararc.domain.repository.RecordingRepository
import com.yuukias.seminararc.domain.repository.ReconstructionRepository
import com.yuukias.seminararc.domain.repository.SaveOcrResultInput
import com.yuukias.seminararc.domain.repository.SaveSummaryDraftInput
import com.yuukias.seminararc.domain.repository.TranscriptRepository
import com.yuukias.seminararc.domain.transcription.TranscriptionProvider
import com.yuukias.seminararc.domain.transcription.TranscriptionRecognition
import com.yuukias.seminararc.domain.transcription.TranscriptionRequest
import com.yuukias.seminararc.domain.transcription.TranscriptionResult
import com.yuukias.seminararc.domain.transcription.TranscriptionSegmentDraft
import com.yuukias.seminararc.domain.usecase.RunTranscriptionForRecordingUseCase
import com.yuukias.seminararc.domain.usecase.RunTranscriptionResult
import java.io.File
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RunTranscriptionForRecordingUseCaseTest {
    @get:Rule
    val temporaryFolder: TemporaryFolder = TemporaryFolder()

    @Test
    fun transcribesCompletedRecordingAndPersistsSegments() = runTest {
        val source = temporaryFolder.newFile("source.m4a")
        val recording = recording()
        val asset = recordingAsset(recording.filePath)
        val transcriptRepository = FakeTranscriptRepository()
        val reconstructionRepository = TranscriptionUseCaseFakeReconstructionRepository(asset)
        val provider = UseCaseFakeTranscriptionProvider(
            TranscriptionResult.Transcribed(
                TranscriptionRecognition(
                    languageHint = TranscriptLanguageHint.EN,
                    segments = listOf(TranscriptionSegmentDraft(1_000L, 2_000L, "Opening claim")),
                ),
            ),
        )
        val useCase = useCase(
            recording = recording,
            asset = asset,
            source = source,
            transcriptRepository = transcriptRepository,
            reconstructionRepository = reconstructionRepository,
            provider = provider,
        )

        val result = useCase(recording.id, TranscriptLanguageHint.AUTO) as RunTranscriptionResult.Transcribed

        assertEquals(TranscriptState.READY, result.transcript.state)
        assertEquals(TranscriptLanguageHint.EN, result.transcript.languageHint)
        assertEquals("Opening claim", result.segments.single().text)
        assertEquals(ProcessingJobState.SUCCEEDED, reconstructionRepository.jobs.single().state)
        assertEquals(asset.id, provider.lastRequest?.sourceAssetId)
    }

    @Test
    fun unreadableRecordingFailsBeforeProviderInvocation() = runTest {
        val provider = UseCaseFakeTranscriptionProvider(
            TranscriptionResult.Failed("should not run", isRetryable = true),
        )
        val useCase = useCase(
            recording = recording(),
            asset = recordingAsset("seminars/1/recordings/source.m4a"),
            source = null,
            provider = provider,
        )

        val result = useCase(1L)

        assertEquals(RunTranscriptionResult.Failed("Recording file is not readable."), result)
        assertNull(provider.lastRequest)
    }

    @Test
    fun providerFailureMarksTranscriptAndJobFailed() = runTest {
        val source = temporaryFolder.newFile("source.m4a")
        val transcriptRepository = FakeTranscriptRepository()
        val reconstructionRepository = TranscriptionUseCaseFakeReconstructionRepository(recordingAsset("seminars/1/recordings/source.m4a"))
        val useCase = useCase(
            recording = recording(),
            asset = recordingAsset("seminars/1/recordings/source.m4a"),
            source = source,
            transcriptRepository = transcriptRepository,
            reconstructionRepository = reconstructionRepository,
            provider = UseCaseFakeTranscriptionProvider(TranscriptionResult.Failed("model missing", isRetryable = false)),
        )

        val result = useCase(1L)

        assertEquals(RunTranscriptionResult.Failed("model missing"), result)
        assertEquals(TranscriptState.FAILED, transcriptRepository.transcripts.single().state)
        assertEquals(ProcessingJobState.FAILED, reconstructionRepository.jobs.single().state)
        assertEquals(false, reconstructionRepository.jobs.single().isRetryable)
    }

    @Test
    fun reusesMatchingExistingProcessingJobFromWorker() = runTest {
        val source = temporaryFolder.newFile("source.m4a")
        val recording = recording()
        val asset = recordingAsset(recording.filePath)
        val reconstructionRepository = TranscriptionUseCaseFakeReconstructionRepository(asset)
        val existingJob = reconstructionRepository.enqueueJob(
            EnqueueProcessingJobInput(
                seminarId = recording.seminarId,
                type = ProcessingJobType.TRANSCRIPTION,
                inputAssetId = asset.id,
                providerId = "fake-transcription",
                providerVersion = "1",
            ),
        )
        val useCase = useCase(
            recording = recording,
            asset = asset,
            source = source,
            reconstructionRepository = reconstructionRepository,
        )

        val result = useCase(recording.id, existingJobId = existingJob.id) as RunTranscriptionResult.Transcribed

        assertEquals(existingJob.id, result.job?.id)
        assertEquals(1, reconstructionRepository.jobs.size)
        assertEquals(ProcessingJobState.SUCCEEDED, reconstructionRepository.jobs.single().state)
    }

    private fun useCase(
        recording: RecordingSession,
        asset: SeminarAsset,
        source: File?,
        transcriptRepository: FakeTranscriptRepository = FakeTranscriptRepository(),
        reconstructionRepository: TranscriptionUseCaseFakeReconstructionRepository =
            TranscriptionUseCaseFakeReconstructionRepository(asset),
        provider: UseCaseFakeTranscriptionProvider = UseCaseFakeTranscriptionProvider(
            TranscriptionResult.Transcribed(
                TranscriptionRecognition(
                    languageHint = TranscriptLanguageHint.AUTO,
                    segments = listOf(TranscriptionSegmentDraft(0L, 1_000L, "text")),
                ),
            ),
        ),
    ): RunTranscriptionForRecordingUseCase {
        return RunTranscriptionForRecordingUseCase(
            recordingRepository = FakeRecordingRepository(recording),
            reconstructionRepository = reconstructionRepository,
            transcriptRepository = transcriptRepository,
            mediaStorageManager = FakeTranscriptionStorage(source),
            transcriptionProvider = provider,
        )
    }
}

private class UseCaseFakeTranscriptionProvider(
    private val result: TranscriptionResult,
) : TranscriptionProvider {
    override val providerId: String = "fake-transcription"
    override val providerVersion: String = "1"
    var lastRequest: TranscriptionRequest? = null
        private set

    override suspend fun transcribe(request: TranscriptionRequest): TranscriptionResult {
        lastRequest = request
        return result
    }
}

private class FakeRecordingRepository(
    private val recording: RecordingSession?,
) : RecordingRepository {
    override fun observeLatestRecordingForSeminar(seminarId: Long): Flow<RecordingSession?> = flowOf(recording)
    override suspend fun getRecording(recordingId: Long): RecordingSession? = recording?.takeIf { it.id == recordingId }
    override suspend fun beginRecordingForActiveSeminar(
        seminarId: Long,
        filePath: String,
        startedAt: Instant,
    ): BeginRecordingResult = error("Not used.")
    override suspend fun completeRecording(recordingId: Long, endedAt: Instant, durationMs: Long): CompleteRecordingResult = error("Not used.")
    override suspend fun failRecording(recordingId: Long, endedAt: Instant, errorMessage: String): FailRecordingResult = error("Not used.")
    override suspend fun getOpenRecordingIds(): List<Long> = emptyList()
    override suspend fun getOpenRecordingIdsForSeminar(seminarId: Long): List<Long> = emptyList()
    override suspend fun failRecordings(recordingIds: List<Long>, endedAt: Instant, errorMessage: String): Int = 0
    override suspend fun failOpenRecordings(endedAt: Instant, errorMessage: String): Int = 0
}

private class TranscriptionUseCaseFakeReconstructionRepository(
    private val sourceAsset: SeminarAsset,
) : ReconstructionRepository {
    val jobs = mutableListOf<ProcessingJob>()
    private var nextJobId = 1L

    override fun observeAssetsForSeminar(seminarId: Long): Flow<List<SeminarAsset>> = flowOf(emptyList())
    override fun observePhotoAssetsForSeminar(seminarId: Long): Flow<List<SeminarAsset>> = flowOf(emptyList())
    override fun observeJobsForSeminar(seminarId: Long): Flow<List<ProcessingJob>> = flowOf(jobs)
    override fun observeOcrResultsForSeminar(seminarId: Long): Flow<List<OcrResult>> = flowOf(emptyList())
    override fun observeTagsForAsset(assetId: Long): Flow<List<AssetTag>> = flowOf(emptyList())
    override fun observeAssetIdsForSystemTag(seminarId: Long, tag: SeminarSystemTag): Flow<List<Long>> = flowOf(emptyList())
    override suspend fun getAsset(assetId: Long): SeminarAsset? = sourceAsset.takeIf { it.id == assetId }
    override suspend fun getAssetByRelativePath(relativePath: String): SeminarAsset? = sourceAsset.takeIf { it.relativePath == relativePath }
    override suspend fun getJob(jobId: Long): ProcessingJob? = jobs.firstOrNull { it.id == jobId }
    override suspend fun recoverInterruptedJobs(): List<ProcessingJob> = emptyList()
    override suspend fun createDerivedAsset(input: CreateDerivedAssetInput): SeminarAsset = error("Not used.")
    override suspend fun enqueueJob(input: EnqueueProcessingJobInput): ProcessingJob {
        val job = ProcessingJob(
            id = nextJobId++,
            seminarId = input.seminarId,
            type = input.type,
            state = ProcessingJobState.QUEUED,
            inputAssetId = input.inputAssetId,
            inputPayloadJson = input.inputPayloadJson,
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
    override suspend fun requeueJob(jobId: Long): ProcessingJob? = null
    override suspend fun markJobRunning(jobId: Long) {
        updateJob(jobId) { it.copy(state = ProcessingJobState.RUNNING, startedAt = NOW) }
    }
    override suspend fun markJobSucceeded(jobId: Long, outputAssetId: Long?) {
        updateJob(jobId) { it.copy(state = ProcessingJobState.SUCCEEDED, outputAssetId = outputAssetId, completedAt = NOW, isRetryable = false) }
    }
    override suspend fun markJobFailed(jobId: Long, message: String, isRetryable: Boolean) {
        updateJob(jobId) { it.copy(state = ProcessingJobState.FAILED, errorMessage = message, isRetryable = isRetryable, completedAt = NOW) }
    }
    override suspend fun markJobCancelled(jobId: Long) = Unit
    override suspend fun saveOcrResult(input: SaveOcrResultInput): OcrResult = error("Not used.")
    override suspend fun editOcrResult(assetId: Long, editedText: String): Boolean = false
    override suspend fun setSystemTag(assetId: Long, tag: SeminarSystemTag, enabled: Boolean) = Unit

    private fun updateJob(jobId: Long, block: (ProcessingJob) -> ProcessingJob) {
        val index = jobs.indexOfFirst { it.id == jobId }
        if (index >= 0) jobs[index] = block(jobs[index])
    }
}

private class FakeTranscriptRepository : TranscriptRepository {
    val transcripts = mutableListOf<Transcript>()
    private val segmentRows = mutableListOf<TranscriptSegment>()
    private var nextTranscriptId = 1L
    private var nextSegmentId = 1L

    override fun observeTranscripts(seminarId: Long): Flow<List<Transcript>> = flowOf(transcripts)
    override fun observeSegments(transcriptId: Long): Flow<List<TranscriptSegment>> = flowOf(segmentRows.filter { it.transcriptId == transcriptId })
    override fun observeSummaryDrafts(seminarId: Long): Flow<List<SummaryDraft>> = flowOf(emptyList())
    override suspend fun getTranscript(transcriptId: Long): Transcript? = transcripts.firstOrNull { it.id == transcriptId }
    override suspend fun getLatestTranscriptForRecording(seminarId: Long, recordingId: Long, providerId: String): Transcript? {
        return transcripts.lastOrNull { it.seminarId == seminarId && it.recordingId == recordingId && it.providerId == providerId }
    }
    override suspend fun getSegments(transcriptId: Long): List<TranscriptSegment> = segmentRows.filter { it.transcriptId == transcriptId }
    override suspend fun createTranscript(input: CreateTranscriptInput): Transcript {
        val transcript = Transcript(
            id = nextTranscriptId++,
            seminarId = input.seminarId,
            recordingId = input.recordingId,
            providerId = input.providerId,
            providerVersion = input.providerVersion,
            languageHint = input.languageHint,
            state = TranscriptState.QUEUED,
            sourceType = input.sourceType,
            sourceAssetId = input.sourceAssetId,
            errorMessage = null,
            createdAt = NOW,
            updatedAt = NOW,
        )
        transcripts += transcript
        return transcript
    }
    override suspend fun markTranscriptRunning(transcriptId: Long): Transcript? {
        return updateTranscript(transcriptId) { it.copy(state = TranscriptState.RUNNING, errorMessage = null) }
    }
    override suspend fun saveTranscriptSegments(
        transcriptId: Long,
        segments: List<TranscriptionSegmentDraft>,
    ): List<TranscriptSegment> {
        segmentRows.removeAll { it.transcriptId == transcriptId }
        val transcript = transcripts.first { it.id == transcriptId }
        val persisted = segments.map { segment ->
            TranscriptSegment(
                id = nextSegmentId++,
                transcriptId = transcriptId,
                seminarId = transcript.seminarId,
                recordingId = transcript.recordingId,
                startOffsetMs = segment.startOffsetMs,
                endOffsetMs = segment.endOffsetMs,
                speakerLabel = segment.speakerLabel,
                language = segment.language,
                text = segment.text,
                confidence = segment.confidence,
                isEdited = false,
                providerSegmentId = segment.providerSegmentId,
                createdAt = NOW,
                updatedAt = NOW,
            )
        }
        segmentRows += persisted
        return persisted
    }
    override suspend fun markTranscriptReady(
        transcriptId: Long,
        languageHint: TranscriptLanguageHint?,
    ): Transcript? {
        return updateTranscript(transcriptId) {
            it.copy(state = TranscriptState.READY, languageHint = languageHint ?: it.languageHint, errorMessage = null)
        }
    }
    override suspend fun markTranscriptFailed(transcriptId: Long, message: String): Transcript? {
        return updateTranscript(transcriptId) { it.copy(state = TranscriptState.FAILED, errorMessage = message) }
    }
    override suspend fun upsertSummaryDraft(input: SaveSummaryDraftInput): SummaryDraft = error("Not used.")

    private fun updateTranscript(transcriptId: Long, block: (Transcript) -> Transcript): Transcript? {
        val index = transcripts.indexOfFirst { it.id == transcriptId }
        if (index < 0) return null
        transcripts[index] = block(transcripts[index])
        return transcripts[index]
    }
}

private class FakeTranscriptionStorage(
    private val source: File?,
) : MediaStorageManager {
    override suspend fun importAbstractPdf(seminarId: Long, sourceUri: String): StoredFile = error("Not used.")
    override suspend fun createRecordingOutputFile(seminarId: Long, startedAt: Instant): RecordingOutputFile = error("Not used.")
    override suspend fun createPhotoOutputFile(seminarId: Long, capturedAt: Instant): PhotoOutputFile = error("Not used.")
    override suspend fun createClipOutputFile(seminarId: Long, clipId: Long): ClipOutputFile = error("Not used.")
    override suspend fun resolveReadableRelativeFile(relativePath: String): File? = source
    override suspend fun deleteRelativeFile(relativePath: String) = Unit
    override suspend fun deleteSeminarMedia(seminarId: Long) = Unit
}

private fun recording(): RecordingSession {
    return RecordingSession(
        id = 1L,
        seminarId = 1L,
        filePath = "seminars/1/recordings/source.m4a",
        startedAt = NOW,
        endedAt = NOW,
        durationMs = 60_000L,
        state = RecordingState.COMPLETED,
        errorMessage = null,
    )
}

private fun recordingAsset(relativePath: String): SeminarAsset {
    return SeminarAsset(
        id = 3L,
        seminarId = 1L,
        type = SeminarAssetType.RECORDING,
        originAssetId = null,
        sourceTimelineEventId = null,
        sourceRecordingId = 1L,
        sourceClipId = null,
        relativePath = relativePath,
        mimeType = "audio/mp4",
        displayName = "source.m4a",
        createdAt = NOW,
        updatedAt = NOW,
    )
}

private val NOW: Instant = Instant.parse("2026-09-08T12:00:00Z")
