package com.yuukias.seminararc.domain

import com.yuukias.seminararc.domain.export.SeminarMarkdownRenderer
import com.yuukias.seminararc.domain.model.ClipState
import com.yuukias.seminararc.domain.model.ExportMediaAsset
import com.yuukias.seminararc.domain.model.ExportMediaKind
import com.yuukias.seminararc.domain.model.ExportFormulaItem
import com.yuukias.seminararc.domain.model.ExportKeySlideItem
import com.yuukias.seminararc.domain.model.ExportReferenceItem
import com.yuukias.seminararc.domain.model.ExportSeminarBrief
import com.yuukias.seminararc.domain.model.ExportSummaryDraft
import com.yuukias.seminararc.domain.model.ExportTimelineItem
import com.yuukias.seminararc.domain.model.ExportTranscript
import com.yuukias.seminararc.domain.model.ExportTranscriptSegment
import com.yuukias.seminararc.domain.model.ExportTranscriptTimelineWindow
import com.yuukias.seminararc.domain.model.SeminarExportDocument
import com.yuukias.seminararc.domain.model.SummaryDraftState
import com.yuukias.seminararc.domain.model.TranscriptLanguageHint
import com.yuukias.seminararc.domain.model.TranscriptSourceType
import com.yuukias.seminararc.domain.model.TranscriptState
import com.yuukias.seminararc.domain.model.TimelineEventType
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class SeminarMarkdownRendererTest {
    @Test
    fun render_outputsDeterministicUnicodeAndLatexLikeMarkdown() {
        val markdown = SeminarMarkdownRenderer().render(
            SeminarExportDocument(
                slug = "seminar-42",
                title = "Bayes # Seminar",
                speaker = "李雷 / Alice",
                affiliation = "CUHK",
                scheduledAt = Instant.parse("2026-08-29T08:00:00Z"),
                location = "LT_1",
                abstractText = "中文 abstract with ${'$'}E=mc^2${'$'} and [link].",
                recordingSummary = "Completed recording: 120000 ms.",
                timelineItems = listOf(
                    ExportTimelineItem(
                        type = TimelineEventType.MARK,
                        offsetMs = 61_000L,
                        text = "important | result",
                        photoPath = null,
                        clipState = ClipState.FAILED,
                        clipPath = null,
                        clipFallbackText = "Clip failed; use full recording from this offset.",
                    ),
                    ExportTimelineItem(
                        type = TimelineEventType.PHOTO,
                        offsetMs = 90_000L,
                        text = null,
                        photoPath = "seminar-42/media/photos/slide 1.jpg",
                        clipState = null,
                        clipPath = null,
                        clipFallbackText = null,
                    ),
                ),
                mediaAssets = listOf(
                    ExportMediaAsset("seminars/42/abstract/a.pdf", "seminar-42/media/abstract/a.pdf", ExportMediaKind.ABSTRACT),
                ),
                skippedMedia = listOf("seminars/42/photos/missing.jpg"),
            ),
        )

        assertEquals(
            """
            # Bayes \# Seminar

            - **Speaker:** 李雷 / Alice
            - **Affiliation:** CUHK
            - **Date/time:** 2026-08-29T08:00:00Z
            - **Location:** LT_1

            ## Abstract

            中文 abstract with ${'$'}E=mc^2${'$'} and [link].

            Abstract PDF: [seminar-42/media/abstract/a.pdf](seminar-42/media/abstract/a.pdf)

            ## Recording

            Completed recording: 120000 ms.

            ## Transcript Review

            No transcript review data.

            ## Generated Summary Drafts

            These are editable generated drafts and do not replace the user-authored Seminar Brief.

            No generated summary drafts.

            ## Formula Results

            No ready formula results.

            ## Timeline

            - `01:01` **Mark** - important \| result
              - Clip: FAILED
              - Fallback: Clip failed; use full recording from this offset.
            - `01:30` **Photo**
              - Photo: ![](seminar-42/media/photos/slide%201.jpg)

            ## Skipped media

            - `seminars/42/photos/missing.jpg`

            """.trimIndent().trim(),
            markdown.trim(),
        )
    }

    @Test
    fun render_includesSeminarBriefConfirmedReferencesAndKeySlides() {
        val markdown = SeminarMarkdownRenderer().render(
            SeminarExportDocument(
                slug = "seminar-7",
                title = "Reference Seminar",
                speaker = null,
                affiliation = null,
                scheduledAt = null,
                location = null,
                abstractText = null,
                recordingSummary = "No recording.",
                brief = ExportSeminarBrief(
                    backgroundContext = "Bayesian inverse problems.",
                    coreQuestion = "Can posterior geometry explain robustness?",
                    methods = "Variational inference.",
                    mainResults = "Stable under perturbation.",
                    keyTakeaways = "Check the proof assumptions.",
                    unresolvedQuestions = "What happens for misspecification?",
                    followUpActions = "Read the appendix.",
                    userNotes = "Ask speaker for code.",
                    references = listOf(
                        ExportReferenceItem(
                            title = "A Probabilistic Theory of Deep Learning",
                            authorsText = "Alice Example, Bob Example",
                            publicationYear = 2024,
                            venue = "Proceedings",
                            doi = "10.1234/example",
                            landingPageUrl = "https://doi.org/10.1234/example",
                            note = "Confirmed from slide OCR.",
                        ),
                    ),
                    keySlides = listOf(
                        ExportKeySlideItem(
                            caption = "Main theorem slide",
                            photoPath = "seminar-7/media/key-slides/slide.jpg",
                        ),
                    ),
                ),
                timelineItems = emptyList(),
                mediaAssets = emptyList(),
                skippedMedia = emptyList(),
            ),
        )

        org.junit.Assert.assertTrue(markdown.contains("## Seminar Brief"))
        org.junit.Assert.assertTrue(markdown.contains("### Confirmed References"))
        org.junit.Assert.assertTrue(markdown.contains("A Probabilistic Theory of Deep Learning"))
        org.junit.Assert.assertTrue(markdown.contains("DOI: `10.1234/example`"))
        org.junit.Assert.assertTrue(markdown.contains("Main theorem slide"))
        org.junit.Assert.assertTrue(markdown.contains("seminar-7/media/key-slides/slide.jpg"))
    }

    @Test
    fun render_includesTranscriptReviewWindowsAndGeneratedDrafts() {
        val markdown = SeminarMarkdownRenderer().render(
            SeminarExportDocument(
                slug = "seminar-9",
                title = "Transcript Seminar",
                speaker = null,
                affiliation = null,
                scheduledAt = null,
                location = null,
                abstractText = null,
                recordingSummary = "Completed recording: 120000 ms.",
                transcripts = listOf(
                    ExportTranscript(
                        id = 5L,
                        recordingId = 2L,
                        providerId = "fake-transcriber",
                        providerVersion = "0.4-contract",
                        languageHint = TranscriptLanguageHint.MIXED,
                        state = TranscriptState.READY,
                        sourceType = TranscriptSourceType.RECORDING,
                        errorMessage = null,
                        segments = listOf(
                            ExportTranscriptSegment(
                                id = 21L,
                                recordingId = 2L,
                                startOffsetMs = 10_000L,
                                endOffsetMs = 20_000L,
                                speakerLabel = "Speaker",
                                language = "en",
                                text = "Key theorem appears here.",
                                confidence = 0.91f,
                                isEdited = true,
                            ),
                        ),
                        timelineWindows = listOf(
                            ExportTranscriptTimelineWindow(
                                eventType = TimelineEventType.PHOTO,
                                eventOffsetMs = 15_000L,
                                windowStartOffsetMs = 0L,
                                windowEndOffsetMs = 60_000L,
                                previewText = "Key theorem appears here.",
                                segmentIds = listOf(21L),
                                photoPath = "seminars/9/photos/slide.jpg",
                            ),
                        ),
                    ),
                ),
                summaryDrafts = listOf(
                    ExportSummaryDraft(
                        id = 6L,
                        providerId = "fake-summary",
                        inputFingerprint = "abc123",
                        state = SummaryDraftState.READY,
                        backgroundContext = "Prior work.",
                        coreQuestion = "What changed?",
                        methods = "Proof sketch.",
                        mainResults = "A stable draft result.",
                        keyTakeaways = "Review before using.",
                        unresolvedQuestions = "Need references.",
                        followUpActions = "Ask speaker.",
                        userNotes = "Generated only.",
                        provenanceJson = """{"segmentIds":[21]}""",
                        errorMessage = null,
                    ),
                ),
                timelineItems = emptyList(),
                mediaAssets = emptyList(),
                skippedMedia = emptyList(),
            ),
        )

        org.junit.Assert.assertTrue(markdown.contains("## Transcript Review"))
        org.junit.Assert.assertTrue(markdown.contains("### Transcript 5"))
        org.junit.Assert.assertTrue(markdown.contains("`00:10-00:20` **Speaker** Key theorem appears here."))
        org.junit.Assert.assertTrue(markdown.contains("#### Timeline windows"))
        org.junit.Assert.assertTrue(markdown.contains("Segment ids: `21`"))
        org.junit.Assert.assertTrue(markdown.contains("## Generated Summary Drafts"))
        org.junit.Assert.assertTrue(markdown.contains("These are editable generated drafts"))
        org.junit.Assert.assertTrue(markdown.contains("A stable draft result."))
        org.junit.Assert.assertTrue(markdown.contains("""Provenance: `{"segmentIds":[21]}`"""))
    }

    @Test
    fun render_includesReadyFormulaResults() {
        val markdown = SeminarMarkdownRenderer().render(
            SeminarExportDocument(
                slug = "seminar-10",
                title = "Formula Seminar",
                speaker = null,
                affiliation = null,
                scheduledAt = null,
                location = null,
                abstractText = null,
                recordingSummary = "No recording.",
                formulas = listOf(
                    ExportFormulaItem(
                        id = 4L,
                        regionId = 3L,
                        label = "Bayes update",
                        sourcePhotoPath = "seminar-10/media/formulas/slide.jpg",
                        normalizedX = 0.1f,
                        normalizedY = 0.2f,
                        normalizedWidth = 0.3f,
                        normalizedHeight = 0.4f,
                        rotationDegrees = 90,
                        providerId = "manual-latex",
                        providerVersion = "0.5-local",
                        latex = "p(\\theta|x) \\propto p(x|\\theta)p(\\theta)",
                        confidence = 1.0f,
                        isEdited = true,
                        provenanceJson = """{"source":"manual"}""",
                    ),
                ),
                timelineItems = emptyList(),
                mediaAssets = emptyList(),
                skippedMedia = emptyList(),
            ),
        )

        org.junit.Assert.assertTrue(markdown.contains("## Formula Results"))
        org.junit.Assert.assertTrue(markdown.contains("### Bayes update"))
        org.junit.Assert.assertTrue(markdown.contains("```latex"))
        org.junit.Assert.assertTrue(markdown.contains("p(\\theta|x) \\propto p(x|\\theta)p(\\theta)"))
        org.junit.Assert.assertTrue(markdown.contains("Provider: `manual-latex` `0.5-local`"))
        org.junit.Assert.assertTrue(markdown.contains("Confidence: 1.00"))
        org.junit.Assert.assertTrue(markdown.contains("Edited: `true`"))
        org.junit.Assert.assertTrue(markdown.contains("seminar-10/media/formulas/slide.jpg"))
        org.junit.Assert.assertTrue(markdown.contains("""Provenance: `{"source":"manual"}`"""))
    }
}
