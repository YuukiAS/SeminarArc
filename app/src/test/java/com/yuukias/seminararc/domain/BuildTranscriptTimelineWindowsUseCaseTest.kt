package com.yuukias.seminararc.domain

import com.yuukias.seminararc.domain.model.AssetTag
import com.yuukias.seminararc.domain.model.OcrResult
import com.yuukias.seminararc.domain.model.ProcessingJob
import com.yuukias.seminararc.domain.model.ProcessingJobState
import com.yuukias.seminararc.domain.model.ProcessingJobType
import com.yuukias.seminararc.domain.model.SeminarAsset
import com.yuukias.seminararc.domain.model.SeminarAssetType
import com.yuukias.seminararc.domain.model.SeminarSystemTag
import com.yuukias.seminararc.domain.model.SummaryDraft
import com.yuukias.seminararc.domain.model.TimelineEvent
import com.yuukias.seminararc.domain.model.TimelineEventType
import com.yuukias.seminararc.domain.model.Transcript
import com.yuukias.seminararc.domain.model.TranscriptLanguageHint
import com.yuukias.seminararc.domain.model.TranscriptSegment
import com.yuukias.seminararc.domain.model.TranscriptSourceType
import com.yuukias.seminararc.domain.model.TranscriptState
import com.yuukias.seminararc.domain.repository.CreateDerivedAssetInput
import com.yuukias.seminararc.domain.repository.CreateTranscriptInput
import com.yuukias.seminararc.domain.repository.EnqueueProcessingJobInput
import com.yuukias.seminararc.domain.repository.ReconstructionRepository
import com.yuukias.seminararc.domain.repository.SaveOcrResultInput
import com.yuukias.seminararc.domain.repository.SaveSummaryDraftInput
import com.yuukias.seminararc.domain.repository.TimelineRepository
import com.yuukias.seminararc.domain.repository.TranscriptRepository
import com.yuukias.seminararc.domain.transcription.TranscriptionSegmentDraft
import com.yuukias.seminararc.domain.usecase.BuildTranscriptTimelineWindowsUseCase
import com.yuukias.seminararc.domain.usecase.TranscriptTimelineWindowInput
import com.yuukias.seminararc.domain.usecase.TranscriptTimelineWindowResult
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BuildTranscriptTimelineWindowsUseCaseTest {
    @Test
    fun matchesTranscriptSegmentsAroundTimelineEventsAndPhotoAssets() = runTest {
        val useCase = useCase(
            events = listOf(
                event(id = 1L, type = TimelineEventType.PHOTO, offsetMs = 60_000L, photoPath = "seminars/1/photos/slide.jpg"),
                event(id = 2L, type = TimelineEventType.MARK, offsetMs = 180_000L),
            ),
            segments = listOf(
                segment(id = 10L, startOffsetMs = 20_000L, endOffsetMs = 35_000L, text = "too early"),
                segment(id = 11L, startOffsetMs = 45_000L, endOffsetMs = 55_000L, text = "before photo"),
                segment(id = 12L, startOffsetMs = 104_000L, endOffsetMs = 110_000L, text = "after photo"),
                segment(id = 13L, startOffsetMs = 175_000L, endOffsetMs = 181_000L, text = "mark context"),
            ),
            assets = listOf(photoAsset(id = 20L, sourceTimelineEventId = 1L, relativePath = "seminars/1/photos/slide.jpg")),
        )

        val result = useCase(
            TranscriptTimelineWindowInput(
                seminarId = 1L,
                transcriptId = 2L,
                windowBeforeMs = 15_000L,
                windowAfterMs = 45_000L,
            ),
        ) as TranscriptTimelineWindowResult.Ready

        assertEquals(listOf(1L, 2L), result.windows.map { it.event.id })
        assertEquals(45_000L, result.windows.first().windowStartOffsetMs)
        assertEquals(105_000L, result.windows.first().windowEndOffsetMs)
        assertEquals(listOf(11L, 12L), result.windows.first().segments.map { it.id })
        assertEquals(20L, result.windows.first().photoAsset?.id)
        assertEquals("before photo after photo", result.windows.first().previewText)
        assertEquals(listOf(13L), result.windows[1].segments.map { it.id })
        assertNull(result.windows[1].photoAsset)
    }

    @Test
    fun filtersSegmentsFromDifferentRecordingsWhenEventHasRecordingId() = runTest {
        val useCase = useCase(
            events = listOf(event(id = 1L, offsetMs = 60_000L, recordingId = 3L)),
            segments = listOf(
                segment(id = 10L, recordingId = 3L, text = "same recording"),
                segment(id = 11L, recordingId = 4L, text = "other recording"),
                segment(id = 12L, recordingId = null, text = "manual transcript"),
            ),
        )

        val result = useCase(
            TranscriptTimelineWindowInput(seminarId = 1L, transcriptId = 2L),
        ) as TranscriptTimelineWindowResult.Ready

        assertEquals(listOf(10L, 12L), result.windows.single().segments.map { it.id })
        assertEquals("same recording manual transcript", result.windows.single().previewText)
    }

    @Test
    fun rejectsTranscriptThatIsNotReady() = runTest {
        val useCase = useCase(transcript = transcript(state = TranscriptState.RUNNING))

        val result = useCase(TranscriptTimelineWindowInput(seminarId = 1L, transcriptId = 2L))

        assertEquals(
            TranscriptTimelineWindowResult.Failed("Only ready transcripts can be matched to timeline windows."),
            result,
        )
    }

    @Test
    fun truncatesPreviewTextAtConfiguredLimit() = runTest {
        val useCase = useCase(
            events = listOf(event(id = 1L, offsetMs = 60_000L)),
            segments = listOf(segment(id = 10L, text = "alpha beta gamma")),
        )

        val result = useCase(
            TranscriptTimelineWindowInput(
                seminarId = 1L,
                transcriptId = 2L,
                previewCharacterLimit = 10,
            ),
        ) as TranscriptTimelineWindowResult.Ready

        assertEquals("alpha beta...", result.windows.single().previewText)
    }

    private fun useCase(
        transcript: Transcript? = transcript(),
        segments: List<TranscriptSegment> = emptyList(),
        events: List<TimelineEvent> = emptyList(),
        assets: List<SeminarAsset> = emptyList(),
    ): BuildTranscriptTimelineWindowsUseCase {
        return BuildTranscriptTimelineWindowsUseCase(
            transcriptRepository = TimelineWindowFakeTranscriptRepository(transcript, segments),
            timelineRepository = TimelineWindowFakeTimelineRepository(events),
            reconstructionRepository = TimelineWindowFakeReconstructionRepository(assets),
        )
    }
}

private class TimelineWindowFakeTranscriptRepository(
    private val transcript: Transcript?,
    private val segments: List<TranscriptSegment>,
) : TranscriptRepository {
    override fun observeTranscripts(seminarId: Long): Flow<List<Transcript>> = flowOf(listOfNotNull(transcript))
    override fun observeSegments(transcriptId: Long): Flow<List<TranscriptSegment>> = flowOf(segments)
    override fun observeSummaryDrafts(seminarId: Long): Flow<List<SummaryDraft>> = flowOf(emptyList())
    override suspend fun getTranscript(transcriptId: Long): Transcript? = transcript?.takeIf { it.id == transcriptId }
    override suspend fun getLatestTranscriptForRecording(seminarId: Long, recordingId: Long, providerId: String): Transcript? = null
    override suspend fun getSegments(transcriptId: Long): List<TranscriptSegment> = segments.filter { it.transcriptId == transcriptId }
    override suspend fun createTranscript(input: CreateTranscriptInput): Transcript = error("Not used.")
    override suspend fun markTranscriptRunning(transcriptId: Long): Transcript? = error("Not used.")
    override suspend fun saveTranscriptSegments(
        transcriptId: Long,
        segments: List<TranscriptionSegmentDraft>,
    ): List<TranscriptSegment> = error("Not used.")
    override suspend fun markTranscriptReady(transcriptId: Long, languageHint: TranscriptLanguageHint?): Transcript? = error("Not used.")
    override suspend fun markTranscriptFailed(transcriptId: Long, message: String): Transcript? = error("Not used.")
    override suspend fun upsertSummaryDraft(input: SaveSummaryDraftInput): SummaryDraft = error("Not used.")
}

private class TimelineWindowFakeTimelineRepository(
    private val events: List<TimelineEvent>,
) : TimelineRepository {
    override fun observeTimelineEvents(seminarId: Long): Flow<List<TimelineEvent>> {
        return flowOf(events.filter { it.seminarId == seminarId })
    }

    override suspend fun addMark(seminarId: Long, recordingId: Long?, offsetMs: Long): TimelineEvent = error("Not used.")
    override suspend fun addNote(seminarId: Long, recordingId: Long?, offsetMs: Long, text: String): TimelineEvent = error("Not used.")
    override suspend fun addQuestion(seminarId: Long, recordingId: Long?, offsetMs: Long, text: String): TimelineEvent = error("Not used.")
    override suspend fun addPhoto(seminarId: Long, recordingId: Long?, offsetMs: Long, photoPath: String): TimelineEvent = error("Not used.")
    override suspend fun deleteEvent(eventId: Long) = error("Not used.")
}

private class TimelineWindowFakeReconstructionRepository(
    private val assets: List<SeminarAsset>,
) : ReconstructionRepository {
    override fun observeAssetsForSeminar(seminarId: Long): Flow<List<SeminarAsset>> = flowOf(assets.filter { it.seminarId == seminarId })
    override fun observePhotoAssetsForSeminar(seminarId: Long): Flow<List<SeminarAsset>> = flowOf(assets.filter { it.seminarId == seminarId })
    override fun observeJobsForSeminar(seminarId: Long): Flow<List<ProcessingJob>> = flowOf(emptyList())
    override fun observeOcrResultsForSeminar(seminarId: Long): Flow<List<OcrResult>> = flowOf(emptyList())
    override fun observeTagsForAsset(assetId: Long): Flow<List<AssetTag>> = flowOf(emptyList())
    override fun observeAssetIdsForSystemTag(seminarId: Long, tag: SeminarSystemTag): Flow<List<Long>> = flowOf(emptyList())
    override suspend fun getAsset(assetId: Long): SeminarAsset? = assets.firstOrNull { it.id == assetId }
    override suspend fun getAssetByRelativePath(relativePath: String): SeminarAsset? = assets.firstOrNull { it.relativePath == relativePath }
    override suspend fun getJob(jobId: Long): ProcessingJob? = null
    override suspend fun recoverInterruptedJobs(): List<ProcessingJob> = emptyList()
    override suspend fun createDerivedAsset(input: CreateDerivedAssetInput): SeminarAsset = error("Not used.")
    override suspend fun enqueueJob(input: EnqueueProcessingJobInput): ProcessingJob = error("Not used.")
    override suspend fun requeueJob(jobId: Long): ProcessingJob? = error("Not used.")
    override suspend fun markJobRunning(jobId: Long) = error("Not used.")
    override suspend fun markJobSucceeded(jobId: Long, outputAssetId: Long?) = error("Not used.")
    override suspend fun markJobFailed(jobId: Long, message: String, isRetryable: Boolean) = error("Not used.")
    override suspend fun markJobCancelled(jobId: Long) = error("Not used.")
    override suspend fun saveOcrResult(input: SaveOcrResultInput): OcrResult = error("Not used.")
    override suspend fun editOcrResult(assetId: Long, editedText: String): Boolean = error("Not used.")
    override suspend fun setSystemTag(assetId: Long, tag: SeminarSystemTag, enabled: Boolean) = error("Not used.")
}

private fun transcript(
    state: TranscriptState = TranscriptState.READY,
): Transcript {
    return Transcript(
        id = 2L,
        seminarId = 1L,
        recordingId = 3L,
        providerId = "fake",
        providerVersion = "1",
        languageHint = TranscriptLanguageHint.MIXED,
        state = state,
        sourceType = TranscriptSourceType.RECORDING,
        sourceAssetId = 20L,
        errorMessage = null,
        createdAt = TIMELINE_WINDOW_NOW,
        updatedAt = TIMELINE_WINDOW_NOW,
    )
}

private fun segment(
    id: Long,
    startOffsetMs: Long = 55_000L,
    endOffsetMs: Long = 65_000L,
    recordingId: Long? = 3L,
    text: String,
): TranscriptSegment {
    return TranscriptSegment(
        id = id,
        transcriptId = 2L,
        seminarId = 1L,
        recordingId = recordingId,
        startOffsetMs = startOffsetMs,
        endOffsetMs = endOffsetMs,
        speakerLabel = null,
        language = null,
        text = text,
        confidence = null,
        isEdited = false,
        providerSegmentId = null,
        createdAt = TIMELINE_WINDOW_NOW,
        updatedAt = TIMELINE_WINDOW_NOW,
    )
}

private fun event(
    id: Long,
    type: TimelineEventType = TimelineEventType.PHOTO,
    offsetMs: Long,
    recordingId: Long? = 3L,
    photoPath: String? = null,
): TimelineEvent {
    return TimelineEvent(
        id = id,
        seminarId = 1L,
        recordingId = recordingId,
        type = type,
        offsetMs = offsetMs,
        createdAt = TIMELINE_WINDOW_NOW.plusMillis(id),
        text = null,
        photoPath = photoPath,
    )
}

private fun photoAsset(
    id: Long,
    sourceTimelineEventId: Long?,
    relativePath: String,
): SeminarAsset {
    return SeminarAsset(
        id = id,
        seminarId = 1L,
        type = SeminarAssetType.PHOTO_ORIGINAL,
        originAssetId = null,
        sourceTimelineEventId = sourceTimelineEventId,
        sourceRecordingId = null,
        sourceClipId = null,
        relativePath = relativePath,
        mimeType = "image/jpeg",
        displayName = "slide.jpg",
        createdAt = TIMELINE_WINDOW_NOW,
        updatedAt = TIMELINE_WINDOW_NOW,
    )
}

private val TIMELINE_WINDOW_NOW: Instant = Instant.parse("2026-09-08T15:00:00Z")
