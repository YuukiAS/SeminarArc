package com.yuukias.seminararc.domain

import com.yuukias.seminararc.domain.model.ReferenceEvidenceQuality
import com.yuukias.seminararc.domain.reference.DoiNormalizer
import com.yuukias.seminararc.domain.reference.ReferenceEvidenceExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReferenceEvidenceExtractorTest {
    @Test
    fun extractSafeDoi_normalizesPrefixWhitespaceAndTrailingPunctuation() {
        assertEquals(
            "10.1145/3368089.3409710",
            DoiNormalizer.extractSafeNormalizedDoi("DOI: 10.1145 / 3368089.3409710,"),
        )
    }

    @Test
    fun extractTitleAuthorsAndYear_fromSelectedReferenceText() {
        val result = ReferenceEvidenceExtractor().extract(
            """
            Neural Ordinary Differential Equations for Irregular Time Series
            Chen, Rubanova and Duvenaud
            NeurIPS 2018
            """.trimIndent(),
        )

        assertEquals("Neural Ordinary Differential Equations for Irregular Time Series", result.title)
        assertEquals(2018, result.year)
        assertEquals(ReferenceEvidenceQuality.HIGH, result.quality)
        assertFalse(result.needsQueryReview)
    }

    @Test
    fun repairVariants_suggestsOcrDigitRepairsWithoutSafeDoi() {
        val repairs = DoiNormalizer.repairVariants("D0I 1O.1145 / 3368089.3409710")

        assertEquals("10.1145/3368089.3409710", repairs.first().normalizedDoi)
        assertEquals(null, DoiNormalizer.extractSafeNormalizedDoi("D0I 1O.1145 / 3368089.3409710"))
    }

    @Test
    fun lowText_requiresQueryReview() {
        val result = ReferenceEvidenceExtractor().extract("abc")

        assertTrue(result.needsQueryReview)
        assertEquals(ReferenceEvidenceQuality.NEEDS_REVIEW, result.quality)
    }
}
