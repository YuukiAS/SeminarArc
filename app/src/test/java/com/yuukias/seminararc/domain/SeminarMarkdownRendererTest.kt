package com.yuukias.seminararc.domain

import com.yuukias.seminararc.domain.export.SeminarMarkdownRenderer
import com.yuukias.seminararc.domain.model.ClipState
import com.yuukias.seminararc.domain.model.ExportMediaAsset
import com.yuukias.seminararc.domain.model.ExportMediaKind
import com.yuukias.seminararc.domain.model.ExportKeySlideItem
import com.yuukias.seminararc.domain.model.ExportReferenceItem
import com.yuukias.seminararc.domain.model.ExportSeminarBrief
import com.yuukias.seminararc.domain.model.ExportTimelineItem
import com.yuukias.seminararc.domain.model.SeminarExportDocument
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
}
