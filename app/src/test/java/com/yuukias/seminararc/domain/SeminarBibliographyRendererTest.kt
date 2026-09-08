package com.yuukias.seminararc.domain

import com.yuukias.seminararc.domain.export.SeminarBibliographyRenderer
import com.yuukias.seminararc.domain.model.ExportReferenceItem
import com.yuukias.seminararc.domain.model.ExportSeminarBrief
import com.yuukias.seminararc.domain.model.SeminarExportDocument
import org.junit.Assert.assertEquals
import org.junit.Test

class SeminarBibliographyRendererTest {
    @Test
    fun renderBibTeX_outputsOnlyProvidedConfirmedReferenceMetadata() {
        val bibTeX = SeminarBibliographyRenderer().renderBibTeX(documentWithReferences())

        assertEquals(
            """
            @article{example2024a,
              title = {A Probabilistic Theory of Deep Learning},
              author = {Alice Example and Bob Example},
              year = {2024},
              journal = {Journal of Test Results},
              doi = {10.1234/example},
              url = {https://doi.org/10.1234/example},
              note = {Confirmed from slide OCR.}
            }

            @misc{lei2026,
              title = {中文 LaTeX result ${'$'}E=mc^2${'$'}},
              author = {Li Lei},
              year = {2026},
              url = {https://example.test/paper}
            }
            """.trimIndent() + "\n",
            bibTeX,
        )
    }

    @Test
    fun renderRis_outputsStandardTaggedReferenceRecords() {
        val ris = SeminarBibliographyRenderer().renderRis(documentWithReferences())

        assertEquals(
            """
            TY  - JOUR
            TI  - A Probabilistic Theory of Deep Learning
            AU  - Alice Example
            AU  - Bob Example
            PY  - 2024
            JO  - Journal of Test Results
            DO  - 10.1234/example
            UR  - https://doi.org/10.1234/example
            N1  - Confirmed from slide OCR.
            ER  -

            TY  - GEN
            TI  - 中文 LaTeX result ${'$'}E=mc^2${'$'}
            AU  - Li Lei
            PY  - 2026
            UR  - https://example.test/paper
            ER  -
            """.trimIndent() + "\n",
            ris,
        )
    }

    @Test
    fun render_returnsEmptyTextWhenNoConfirmedReferencesAreInExportDocument() {
        val document = SeminarExportDocument(
            slug = "seminar-1",
            title = "No references",
            speaker = null,
            affiliation = null,
            scheduledAt = null,
            location = null,
            abstractText = null,
            recordingSummary = "No recording.",
            timelineItems = emptyList(),
            mediaAssets = emptyList(),
            skippedMedia = emptyList(),
        )

        assertEquals("", SeminarBibliographyRenderer().renderBibTeX(document))
        assertEquals("", SeminarBibliographyRenderer().renderRis(document))
    }
}

private fun documentWithReferences(): SeminarExportDocument {
    return SeminarExportDocument(
        slug = "seminar-7",
        title = "Reference Seminar",
        speaker = null,
        affiliation = null,
        scheduledAt = null,
        location = null,
        abstractText = null,
        recordingSummary = "No recording.",
        brief = ExportSeminarBrief(
            backgroundContext = "",
            coreQuestion = "",
            methods = "",
            mainResults = "",
            keyTakeaways = "",
            unresolvedQuestions = "",
            followUpActions = "",
            userNotes = "",
            references = listOf(
                ExportReferenceItem(
                    title = "A Probabilistic Theory of Deep Learning",
                    authors = listOf("Alice Example", "Bob Example"),
                    authorsText = "Alice Example; Bob Example",
                    publicationYear = 2024,
                    venue = "Proceedings",
                    sourceTitle = "Journal of Test Results",
                    publicationType = "journal-article",
                    doi = "10.1234/example",
                    landingPageUrl = "https://doi.org/10.1234/example",
                    note = "Confirmed from slide OCR.",
                ),
                ExportReferenceItem(
                    title = "中文 LaTeX result ${'$'}E=mc^2${'$'}",
                    authors = listOf("Li Lei"),
                    authorsText = "Li Lei",
                    publicationYear = 2026,
                    venue = null,
                    sourceTitle = null,
                    publicationType = null,
                    doi = null,
                    landingPageUrl = "https://example.test/paper",
                    note = null,
                ),
            ),
            keySlides = emptyList(),
        ),
        timelineItems = emptyList(),
        mediaAssets = emptyList(),
        skippedMedia = emptyList(),
    )
}
