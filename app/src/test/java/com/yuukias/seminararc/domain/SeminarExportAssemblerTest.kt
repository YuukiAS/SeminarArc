package com.yuukias.seminararc.domain

import com.yuukias.seminararc.domain.export.TranscriptExportBundle
import com.yuukias.seminararc.domain.export.SeminarExportAssembler
import com.yuukias.seminararc.domain.model.AbstractAttachment
import com.yuukias.seminararc.domain.model.AudioClip
import com.yuukias.seminararc.domain.model.ClipState
import com.yuukias.seminararc.domain.model.ExportMediaAsset
import com.yuukias.seminararc.domain.model.ExportMediaKind
import com.yuukias.seminararc.domain.model.FormulaRegion
import com.yuukias.seminararc.domain.model.FormulaResult
import com.yuukias.seminararc.domain.model.FormulaResultState
import com.yuukias.seminararc.domain.model.RecordingSession
import com.yuukias.seminararc.domain.model.RecordingState
import com.yuukias.seminararc.domain.model.SeminarDetail
import com.yuukias.seminararc.domain.model.SeminarStatus
import com.yuukias.seminararc.domain.model.SummaryDraft
import com.yuukias.seminararc.domain.model.SummaryDraftState
import com.yuukias.seminararc.domain.model.TimelineEvent
import com.yuukias.seminararc.domain.model.TimelineEventType
import com.yuukias.seminararc.domain.model.Transcript
import com.yuukias.seminararc.domain.model.TranscriptLanguageHint
import com.yuukias.seminararc.domain.model.TranscriptSegment
import com.yuukias.seminararc.domain.model.TranscriptSourceType
import com.yuukias.seminararc.domain.model.TranscriptState
import com.yuukias.seminararc.domain.usecase.TranscriptTimelineWindow
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SeminarExportAssemblerTest {
    @Test
    fun assemble_mapsSeminarTimelineClipsAndReadableMediaIntoExportDocument() = runTest {
        val document = SeminarExportAssembler().assemble(
            detail = SeminarDetail(
                id = 42L,
                title = "中文 Bayesian Seminar",
                speaker = "Alice",
                affiliation = "CUHK",
                scheduledAt = Instant.parse("2026-08-29T08:00:00Z"),
                location = "LT1",
                abstractText = "Abstract",
                abstractAttachment = AbstractAttachment("abstract.pdf", "seminars/42/abstract/abstract.pdf"),
                status = SeminarStatus.COMPLETED,
                sessionStartedAt = Instant.parse("2026-08-29T08:00:00Z"),
                sessionEndedAt = Instant.parse("2026-08-29T09:00:00Z"),
                rating = 5,
                isFavorite = true,
                photoCount = 2,
                clipCount = 1,
                recordingDurationMs = 3_600_000L,
                timelinePreview = emptyList(),
            ),
            events = listOf(
                timelineEvent(id = 2L, type = TimelineEventType.PHOTO, offsetMs = 90_000L, photoPath = "seminars/42/photos/missing.jpg"),
                timelineEvent(id = 1L, type = TimelineEventType.MARK, offsetMs = 30_000L, text = "Important theorem"),
                timelineEvent(id = 3L, type = TimelineEventType.QUESTION, offsetMs = 120_000L, text = "Ask later"),
            ),
            recordings = listOf(
                RecordingSession(
                    id = 7L,
                    seminarId = 42L,
                    filePath = "seminars/42/recordings/full.m4a",
                    startedAt = Instant.parse("2026-08-29T08:00:00Z"),
                    endedAt = Instant.parse("2026-08-29T09:00:00Z"),
                    durationMs = 3_600_000L,
                    state = RecordingState.COMPLETED,
                    errorMessage = null,
                ),
            ),
            clips = listOf(
                AudioClip(
                    id = 11L,
                    seminarId = 42L,
                    recordingId = 7L,
                    sourceEventId = 1L,
                    startOffsetMs = 20_000L,
                    endOffsetMs = 40_000L,
                    filePath = "seminars/42/clips/clip-1.m4a",
                    state = ClipState.READY,
                    errorMessage = null,
                    retryCount = 0,
                ),
                AudioClip(
                    id = 12L,
                    seminarId = 42L,
                    recordingId = 7L,
                    sourceEventId = 3L,
                    startOffsetMs = 110_000L,
                    endOffsetMs = 130_000L,
                    filePath = null,
                    state = ClipState.FAILED,
                    errorMessage = "source missing",
                    retryCount = 1,
                ),
            ),
        ) { path -> path != "seminars/42/photos/missing.jpg" }

        assertEquals("中文-bayesian-seminar-42", document.slug)
        assertEquals("Completed recording: 3600000 ms.", document.recordingSummary)
        assertEquals(
            listOf(
                ExportMediaAsset(
                    sourceRelativePath = "seminars/42/abstract/abstract.pdf",
                    exportRelativePath = "中文-bayesian-seminar-42/media/abstract/abstract.pdf",
                    kind = ExportMediaKind.ABSTRACT,
                ),
                ExportMediaAsset(
                    sourceRelativePath = "seminars/42/clips/clip-1.m4a",
                    exportRelativePath = "中文-bayesian-seminar-42/media/clips/clip-1.m4a",
                    kind = ExportMediaKind.CLIP,
                ),
            ),
            document.mediaAssets,
        )
        assertEquals(listOf("seminars/42/photos/missing.jpg"), document.skippedMedia)
        assertEquals(listOf(30_000L, 90_000L, 120_000L), document.timelineItems.map { it.offsetMs })
        assertEquals("中文-bayesian-seminar-42/media/clips/clip-1.m4a", document.timelineItems[0].clipPath)
        assertEquals("中文-bayesian-seminar-42/media/photos/missing.jpg", document.timelineItems[1].photoPath)
        assertEquals("Clip failed; use full recording from this offset.", document.timelineItems[2].clipFallbackText)
    }

    @Test
    fun assemble_mapsTranscriptReviewDataAndSummaryDraftsIntoExportDocument() = runTest {
        val document = SeminarExportAssembler().assemble(
            detail = seminarDetail(id = 42L, title = "Transcript Export"),
            events = emptyList(),
            recordings = emptyList(),
            clips = emptyList(),
            transcriptBundles = listOf(
                TranscriptExportBundle(
                    transcript = transcript(id = 8L),
                    segments = listOf(transcriptSegment(id = 14L)),
                    timelineWindows = listOf(
                        TranscriptTimelineWindow(
                            event = timelineEvent(id = 3L, type = TimelineEventType.PHOTO, offsetMs = 60_000L),
                            windowStartOffsetMs = 30_000L,
                            windowEndOffsetMs = 105_000L,
                            segments = listOf(transcriptSegment(id = 14L)),
                            photoAsset = null,
                            previewText = "Important proof context.",
                        ),
                    ),
                ),
            ),
            summaryDrafts = listOf(summaryDraft(id = 12L)),
        ) { true }

        assertEquals(listOf(8L), document.transcripts.map { it.id })
        assertEquals(listOf(14L), document.transcripts.single().segments.map { it.id })
        assertEquals(listOf(14L), document.transcripts.single().timelineWindows.single().segmentIds)
        assertEquals("Important proof context.", document.transcripts.single().timelineWindows.single().previewText)
        assertEquals(listOf(12L), document.summaryDrafts.map { it.id })
        assertEquals("A generated draft result.", document.summaryDrafts.single().mainResults)
    }

    @Test
    fun assemble_mapsOnlyReadyFormulaResultsIntoExportDocument() = runTest {
        val document = SeminarExportAssembler().assemble(
            detail = seminarDetail(id = 42L, title = "Formula Export"),
            events = emptyList(),
            recordings = emptyList(),
            clips = emptyList(),
            formulaRegions = listOf(
                formulaRegion(id = 1L, sourceAssetId = 10L, label = "Posterior"),
                formulaRegion(id = 2L, sourceAssetId = 11L, label = "Failed"),
            ),
            formulaResults = listOf(
                formulaResult(id = 20L, regionId = 1L, state = FormulaResultState.READY, latex = "E = mc^2", updatedAt = Instant.parse("2026-08-29T08:05:00Z")),
                formulaResult(id = 21L, regionId = 1L, state = FormulaResultState.READY, latex = "E = mc^2 + c", updatedAt = Instant.parse("2026-08-29T08:10:00Z")),
                formulaResult(id = 22L, regionId = 2L, state = FormulaResultState.FAILED, latex = "", updatedAt = Instant.parse("2026-08-29T08:11:00Z")),
            ),
        ) { true }

        assertEquals(1, document.formulas.size)
        assertEquals("Posterior", document.formulas.single().label)
        assertEquals("E = mc^2 + c", document.formulas.single().latex)
        assertEquals("formula-export-42/media/formulas/formula.jpg", document.formulas.single().sourcePhotoPath)
        assertEquals(
            ExportMediaAsset(
                sourceRelativePath = "seminars/42/photos/formula.jpg",
                exportRelativePath = "formula-export-42/media/formulas/formula.jpg",
                kind = ExportMediaKind.FORMULA_SOURCE,
            ),
            document.mediaAssets.single(),
        )
    }
}

private fun seminarDetail(id: Long, title: String): SeminarDetail {
    return SeminarDetail(
        id = id,
        title = title,
        speaker = "Alice",
        affiliation = "CUHK",
        scheduledAt = Instant.parse("2026-08-29T08:00:00Z"),
        location = "LT1",
        abstractText = "Abstract",
        abstractAttachment = null,
        status = SeminarStatus.COMPLETED,
        sessionStartedAt = Instant.parse("2026-08-29T08:00:00Z"),
        sessionEndedAt = Instant.parse("2026-08-29T09:00:00Z"),
        rating = 5,
        isFavorite = true,
        photoCount = 0,
        clipCount = 0,
        recordingDurationMs = 3_600_000L,
        timelinePreview = emptyList(),
    )
}

private fun timelineEvent(
    id: Long,
    type: TimelineEventType,
    offsetMs: Long,
    text: String? = null,
    photoPath: String? = null,
): TimelineEvent = TimelineEvent(
    id = id,
    seminarId = 42L,
    recordingId = 7L,
    type = type,
    offsetMs = offsetMs,
    createdAt = Instant.parse("2026-08-29T08:00:00Z"),
    text = text,
    photoPath = photoPath,
)

private fun transcript(id: Long): Transcript {
    return Transcript(
        id = id,
        seminarId = 42L,
        recordingId = 7L,
        providerId = "fake-transcriber",
        providerVersion = "0.4-contract",
        languageHint = TranscriptLanguageHint.MIXED,
        state = TranscriptState.READY,
        sourceType = TranscriptSourceType.RECORDING,
        sourceAssetId = null,
        errorMessage = null,
        createdAt = Instant.parse("2026-08-29T08:00:00Z"),
        updatedAt = Instant.parse("2026-08-29T08:10:00Z"),
    )
}

private fun transcriptSegment(id: Long): TranscriptSegment {
    return TranscriptSegment(
        id = id,
        transcriptId = 8L,
        seminarId = 42L,
        recordingId = 7L,
        startOffsetMs = 45_000L,
        endOffsetMs = 70_000L,
        speakerLabel = "Speaker",
        language = "en",
        text = "Important proof context.",
        confidence = 0.9f,
        isEdited = false,
        providerSegmentId = "s-$id",
        createdAt = Instant.parse("2026-08-29T08:00:00Z"),
        updatedAt = Instant.parse("2026-08-29T08:10:00Z"),
    )
}

private fun summaryDraft(id: Long): SummaryDraft {
    return SummaryDraft(
        id = id,
        seminarId = 42L,
        providerId = "fake-summary",
        inputFingerprint = "fingerprint",
        state = SummaryDraftState.READY,
        backgroundContext = "Background",
        coreQuestion = "Question",
        methods = "Methods",
        mainResults = "A generated draft result.",
        keyTakeaways = "Takeaways",
        unresolvedQuestions = "Open questions",
        followUpActions = "Follow up",
        userNotes = "Notes",
        provenanceJson = "{}",
        errorMessage = null,
        createdAt = Instant.parse("2026-08-29T08:00:00Z"),
        updatedAt = Instant.parse("2026-08-29T08:10:00Z"),
    )
}

private fun formulaRegion(
    id: Long,
    sourceAssetId: Long,
    label: String,
): FormulaRegion {
    return FormulaRegion(
        id = id,
        seminarId = 42L,
        sourceAssetId = sourceAssetId,
        sourcePhotoPath = "seminars/42/photos/formula.jpg",
        normalizedX = 0.1f,
        normalizedY = 0.2f,
        normalizedWidth = 0.3f,
        normalizedHeight = 0.4f,
        rotationDegrees = 0,
        label = label,
        createdAt = Instant.parse("2026-08-29T08:00:00Z"),
        updatedAt = Instant.parse("2026-08-29T08:00:00Z"),
    )
}

private fun formulaResult(
    id: Long,
    regionId: Long,
    state: FormulaResultState,
    latex: String,
    updatedAt: Instant,
): FormulaResult {
    return FormulaResult(
        id = id,
        seminarId = 42L,
        regionId = regionId,
        providerId = "manual-latex",
        providerVersion = "0.5-local",
        state = state,
        latex = latex,
        confidence = 1.0f,
        isEdited = true,
        errorMessage = null,
        provenanceJson = """{"source":"manual"}""",
        createdAt = Instant.parse("2026-08-29T08:00:00Z"),
        updatedAt = updatedAt,
    )
}
