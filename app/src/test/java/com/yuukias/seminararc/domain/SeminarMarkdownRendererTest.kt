package com.yuukias.seminararc.domain

import com.yuukias.seminararc.domain.export.SeminarMarkdownRenderer
import com.yuukias.seminararc.domain.model.ClipState
import com.yuukias.seminararc.domain.model.ExportMediaAsset
import com.yuukias.seminararc.domain.model.ExportMediaKind
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
}
