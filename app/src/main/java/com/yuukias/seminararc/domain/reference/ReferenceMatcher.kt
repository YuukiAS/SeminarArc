package com.yuukias.seminararc.domain.reference

import com.yuukias.seminararc.domain.model.REFERENCE_MATCHER_VERSION
import com.yuukias.seminararc.domain.model.ReferenceConfidenceBand
import com.yuukias.seminararc.domain.model.ReferenceEvidence
import com.yuukias.seminararc.domain.model.ReferenceLookupQuery
import com.yuukias.seminararc.domain.model.ReferenceLookupQueryType
import com.yuukias.seminararc.domain.model.ReferenceProviderResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Locale
import javax.inject.Inject
import kotlin.math.abs

class ReferenceMatcher @Inject constructor() {
    fun score(
        query: ReferenceLookupQuery,
        evidence: List<ReferenceEvidence>,
        result: ReferenceProviderResult,
        crossProviderCount: Int,
    ): ReferenceMatch {
        val reasons = mutableListOf<ReferenceMatchReason>()
        val queryDoi = query.doi?.let(DoiNormalizer::normalizeSafe)
        val resultDoi = result.doi?.let(DoiNormalizer::normalizeSafe)
        if (queryDoi != null && queryDoi == resultDoi) {
            reasons += ReferenceMatchReason("doi", 100, "Normalized DOI matched provider metadata.")
            return ReferenceMatch(100, ReferenceConfidenceBand.HIGH_CONFIDENCE, reasons)
        }

        var score = 0
        val titleScore = titleSimilarity(query.title.orEmpty(), result.title)
        if (titleScore > 0) {
            val points = (titleScore * 35).toInt().coerceIn(0, 35)
            score += points
            reasons += ReferenceMatchReason("title", points, "Title tokens overlap with selected evidence.")
        }
        val authorPoints = authorOverlap(query.authors, result.authors)
        if (authorPoints > 0) {
            score += authorPoints
            reasons += ReferenceMatchReason("authors", authorPoints, "Author clues overlap provider authors.")
        }
        val yearPoints = yearProximity(query.year, result.publicationYear)
        if (yearPoints > 0) {
            score += yearPoints
            reasons += ReferenceMatchReason("year", yearPoints, "Publication year is close to selected evidence.")
        }
        if (!query.venue.isNullOrBlank() && !result.venue.isNullOrBlank() &&
            result.venue.contains(query.venue, ignoreCase = true)
        ) {
            score += 10
            reasons += ReferenceMatchReason("venue", 10, "Venue/source clue matched provider metadata.")
        }
        if (crossProviderCount > 1) {
            val points = ((crossProviderCount - 1) * 5).coerceAtMost(10)
            score += points
            reasons += ReferenceMatchReason("providers", points, "Multiple providers returned the same work.")
        }
        val qualityPoints = evidence.maxOfOrNull { it.evidenceQuality.ordinal }
            ?.let { ordinal -> (3 - ordinal).coerceAtLeast(0) * 3 }
            ?.coerceAtMost(10) ?: 0
        if (qualityPoints > 0) {
            score += qualityPoints
            reasons += ReferenceMatchReason("evidence", qualityPoints, "Selected evidence quality supports this match.")
        }
        if (query.queryType == ReferenceLookupQueryType.TITLE_AUTHOR_YEAR && titleScore >= 0.75) {
            score += 5
            reasons += ReferenceMatchReason("query", 5, "Manual title/author/year query matched strongly.")
        }
        val band = when {
            score >= 75 && titleScore >= 0.5 && (authorPoints > 0 || yearPoints > 0) -> ReferenceConfidenceBand.HIGH_CONFIDENCE
            score >= 50 -> ReferenceConfidenceBand.POSSIBLE_MATCH
            else -> ReferenceConfidenceBand.NEEDS_REVIEW
        }
        return ReferenceMatch(score.coerceIn(0, 100), band, reasons.take(5))
    }

    fun reasonJson(reasons: List<ReferenceMatchReason>): String = Json.encodeToString(reasons)

    private fun titleSimilarity(a: String, b: String): Double {
        val left = a.normalizeTitle().tokens()
        val right = b.normalizeTitle().tokens()
        if (left.isEmpty() || right.isEmpty()) return 0.0
        val intersection = left.intersect(right).size.toDouble()
        val union = left.union(right).size.toDouble()
        return intersection / union.coerceAtLeast(1.0)
    }

    private fun authorOverlap(queryAuthors: List<String>, resultAuthors: List<String>): Int {
        val left = queryAuthors.mapNotNull { it.familyName() }.toSet()
        val right = resultAuthors.mapNotNull { it.familyName() }.toSet()
        if (left.isEmpty() || right.isEmpty()) return 0
        val overlap = left.intersect(right).size
        val base = (overlap * 20 / left.size.coerceAtLeast(1)).coerceAtMost(18)
        return if (left.firstOrNull() == right.firstOrNull()) (base + 2).coerceAtMost(20) else base
    }

    private fun yearProximity(queryYear: Int?, resultYear: Int?): Int {
        if (queryYear == null || resultYear == null) return 0
        return when (abs(queryYear - resultYear)) {
            0 -> 10
            1 -> 6
            else -> 0
        }
    }
}

data class ReferenceMatch(
    val score: Int,
    val confidenceBand: ReferenceConfidenceBand,
    val reasons: List<ReferenceMatchReason>,
    val matcherVersion: String = REFERENCE_MATCHER_VERSION,
)

@Serializable
data class ReferenceMatchReason(
    val component: String,
    val points: Int,
    val text: String,
)

private fun String.tokens(): Set<String> = split(' ')
    .filter { it.length >= 3 || it.any { char -> char.code > 127 } }
    .toSet()

private fun String.familyName(): String? {
    return trim()
        .split(Regex("\\s+"))
        .lastOrNull()
        ?.lowercase(Locale.US)
        ?.replace(Regex("[^a-z\\u4e00-\\u9fff]"), "")
        ?.takeIf { it.isNotBlank() }
}
