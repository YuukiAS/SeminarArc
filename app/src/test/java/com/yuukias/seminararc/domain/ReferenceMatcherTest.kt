package com.yuukias.seminararc.domain

import com.yuukias.seminararc.domain.model.REFERENCE_MATCHER_VERSION
import com.yuukias.seminararc.domain.model.ReferenceConfidenceBand
import com.yuukias.seminararc.domain.model.ReferenceLookupProviderId
import com.yuukias.seminararc.domain.model.ReferenceLookupQuery
import com.yuukias.seminararc.domain.model.ReferenceLookupQueryType
import com.yuukias.seminararc.domain.model.ReferenceProviderResult
import com.yuukias.seminararc.domain.reference.ReferenceMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReferenceMatcherTest {
    @Test
    fun exactNormalizedDoi_isHighConfidenceButStillVersioned() {
        val match = ReferenceMatcher().score(
            query = ReferenceLookupQuery(
                provider = ReferenceLookupProviderId.CROSSREF,
                queryType = ReferenceLookupQueryType.DOI,
                doi = "10.1145/3368089.3409710",
            ),
            evidence = emptyList(),
            result = result(doi = "10.1145/3368089.3409710"),
            crossProviderCount = 1,
        )

        assertEquals(100, match.score)
        assertEquals(ReferenceConfidenceBand.HIGH_CONFIDENCE, match.confidenceBand)
        assertEquals(REFERENCE_MATCHER_VERSION, match.matcherVersion)
    }

    @Test
    fun titleAuthorYearMatch_scoresAsPossibleOrHighWithoutPretendingProbability() {
        val match = ReferenceMatcher().score(
            query = ReferenceLookupQuery(
                provider = ReferenceLookupProviderId.CROSSREF,
                queryType = ReferenceLookupQueryType.TITLE_AUTHOR_YEAR,
                title = "Neural Ordinary Differential Equations for Irregular Time Series",
                authors = listOf("Chen", "Rubanova"),
                year = 2018,
            ),
            evidence = emptyList(),
            result = result(
                doi = null,
                title = "Neural Ordinary Differential Equations for Irregular Time Series",
                authors = listOf("Ricky T. Q. Chen", "Yulia Rubanova"),
                year = 2018,
            ),
            crossProviderCount = 2,
        )

        assertTrue(match.score >= 50)
        assertTrue(match.reasons.any { it.component == "title" })
        assertTrue(match.confidenceBand in listOf(ReferenceConfidenceBand.HIGH_CONFIDENCE, ReferenceConfidenceBand.POSSIBLE_MATCH))
    }

    private fun result(
        doi: String?,
        title: String = "A Reference Candidate",
        authors: List<String> = listOf("Alice Example"),
        year: Int? = 2020,
    ): ReferenceProviderResult {
        return ReferenceProviderResult(
            provider = ReferenceLookupProviderId.CROSSREF,
            providerWorkId = doi ?: title,
            doi = doi,
            title = title,
            authors = authors,
            publicationYear = year,
            venue = "Proceedings",
            sourceTitle = "Proceedings",
            publicationType = "proceedings-article",
            landingPageUrl = "https://doi.org/$doi",
            openAccessUrl = null,
            licenseUrl = null,
            providerRawScore = null,
            providerPayloadJson = null,
        )
    }
}
