package com.yuukias.seminararc.domain

import com.yuukias.seminararc.domain.export.NotionReadyBlockType
import com.yuukias.seminararc.domain.export.SeminarNotionReadyRenderer
import com.yuukias.seminararc.domain.model.ExportMediaAsset
import com.yuukias.seminararc.domain.model.ExportMediaKind
import com.yuukias.seminararc.domain.model.ExportFormulaItem
import com.yuukias.seminararc.domain.model.ExportSummaryDraft
import com.yuukias.seminararc.domain.model.ExportTimelineItem
import com.yuukias.seminararc.domain.model.ExportTranscript
import com.yuukias.seminararc.domain.model.ExportTranscriptSegment
import com.yuukias.seminararc.domain.model.ExportTranscriptTimelineWindow
import com.yuukias.seminararc.domain.model.SeminarExportDocument
import com.yuukias.seminararc.domain.model.SummaryDraftState
import com.yuukias.seminararc.domain.model.TimelineEventType
import com.yuukias.seminararc.domain.model.TranscriptLanguageHint
import com.yuukias.seminararc.domain.model.TranscriptSourceType
import com.yuukias.seminararc.domain.model.TranscriptState
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeminarNotionReadyRendererTest {
    @Test
    fun renderBuildsLocalBlockContractForTranscriptsSummaryDraftsAndMedia() {
        val rendered = SeminarNotionReadyRenderer().render(exportDocument())

        assertEquals("Notion Seminar", rendered.title)
        assertEquals("notion-seminar-42", rendered.sourceSlug)
        assertTrue(rendered.blocks.any { it.type == NotionReadyBlockType.HEADING_2 && it.text == "Transcript Review" })
        assertTrue(rendered.blocks.any { it.text.contains("00:45-01:10 Important proof context.") })
        assertTrue(rendered.blocks.any { it.type == NotionReadyBlockType.HEADING_2 && it.text == "Generated Summary Drafts" })
        assertTrue(rendered.blocks.any { it.type == NotionReadyBlockType.CALLOUT && it.text.contains("editable generated drafts") })
        assertTrue(rendered.blocks.any { it.type == NotionReadyBlockType.HEADING_2 && it.text == "Formula Results" })
        assertTrue(rendered.blocks.any { it.type == NotionReadyBlockType.CODE && it.text == "E = mc^2" })
        assertTrue(rendered.blocks.any { it.type == NotionReadyBlockType.IMAGE && it.localRelativePath == "notion-seminar-42/media/photos/photo.jpg" })
        assertTrue(rendered.blocks.none { it.text.contains("token", ignoreCase = true) })
    }

    @Test
    fun renderMarkdownPreviewIsLocalAndUploadFree() {
        val preview = SeminarNotionReadyRenderer().renderMarkdownPreview(exportDocument())

        assertTrue(preview.contains("Notion-ready local preview. This file is not uploaded"))
        assertTrue(preview.contains("## Transcript Review"))
        assertTrue(preview.contains("## Generated Summary Drafts"))
        assertTrue(preview.contains("## Formula Results"))
        assertTrue(preview.contains("```latex\nE = mc^2\n```"))
        assertTrue(preview.contains("![Timeline photo](notion-seminar-42/media/photos/photo.jpg)"))
    }
}

private fun exportDocument(): SeminarExportDocument {
    return SeminarExportDocument(
        slug = "notion-seminar-42",
        title = "Notion Seminar",
        speaker = "Alice",
        affiliation = "CUHK",
        scheduledAt = Instant.parse("2026-09-08T09:00:00Z"),
        location = "LT1",
        abstractText = "Abstract",
        recordingSummary = "Completed recording: 60000 ms.",
        transcripts = listOf(
            ExportTranscript(
                id = 8L,
                recordingId = 7L,
                providerId = "fake-transcriber",
                providerVersion = "0.4-contract",
                languageHint = TranscriptLanguageHint.MIXED,
                state = TranscriptState.READY,
                sourceType = TranscriptSourceType.RECORDING,
                errorMessage = null,
                segments = listOf(
                    ExportTranscriptSegment(
                        id = 14L,
                        recordingId = 7L,
                        startOffsetMs = 45_000L,
                        endOffsetMs = 70_000L,
                        speakerLabel = "Speaker",
                        language = "en",
                        text = "Important proof context.",
                        confidence = 0.9f,
                        isEdited = false,
                    ),
                ),
                timelineWindows = listOf(
                    ExportTranscriptTimelineWindow(
                        eventType = TimelineEventType.PHOTO,
                        eventOffsetMs = 60_000L,
                        windowStartOffsetMs = 30_000L,
                        windowEndOffsetMs = 105_000L,
                        previewText = "Important proof context.",
                        segmentIds = listOf(14L),
                        photoPath = "notion-seminar-42/media/photos/photo.jpg",
                    ),
                ),
            ),
        ),
        summaryDrafts = listOf(
            ExportSummaryDraft(
                id = 12L,
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
            ),
        ),
        formulas = listOf(
            ExportFormulaItem(
                id = 15L,
                regionId = 3L,
                label = "Mass energy",
                sourcePhotoPath = "notion-seminar-42/media/formulas/formula.jpg",
                normalizedX = 0.1f,
                normalizedY = 0.2f,
                normalizedWidth = 0.3f,
                normalizedHeight = 0.4f,
                rotationDegrees = 0,
                providerId = "manual-latex",
                providerVersion = "0.5-local",
                latex = "E = mc^2",
                confidence = 1.0f,
                isEdited = true,
                provenanceJson = "{}",
            ),
        ),
        timelineItems = listOf(
            ExportTimelineItem(
                type = TimelineEventType.PHOTO,
                offsetMs = 60_000L,
                text = "Slide photo",
                photoPath = "notion-seminar-42/media/photos/photo.jpg",
                clipState = null,
                clipPath = null,
                clipFallbackText = null,
            ),
        ),
        mediaAssets = listOf(
            ExportMediaAsset(
                sourceRelativePath = "seminars/42/photos/photo.jpg",
                exportRelativePath = "notion-seminar-42/media/photos/photo.jpg",
                kind = ExportMediaKind.PHOTO,
            ),
        ),
        skippedMedia = emptyList(),
    )
}
