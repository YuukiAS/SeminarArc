package com.yuukias.seminararc.domain.reference

import com.yuukias.seminararc.domain.model.ReferenceEvidenceQuality
import java.time.Year
import java.util.Locale
import javax.inject.Inject

class ReferenceEvidenceExtractor @Inject constructor() {
    fun extract(text: String): ExtractedReferenceEvidence {
        val cleaned = text.trim().replace(Regex("[ \\t]+"), " ")
        val lines = cleaned.lines().map { it.trim() }.filter { it.isNotBlank() }
        val safeDoi = DoiNormalizer.extractSafeNormalizedDoi(cleaned)
        val titleCandidates = lines
            .filter { line -> line.looksLikeTitle() }
            .sortedByDescending { line -> line.titleScore() }
        val title = titleCandidates.firstOrNull()
        val authors = lines.firstOrNull { it.looksLikeAuthors() }
            ?.split(Regex("\\s+(?:and|&)\\s+|,|;"))
            ?.map { it.trim() }
            ?.filter { it.length >= 3 }
            .orEmpty()
        val year = Regex("\\b(19\\d{2}|20\\d{2})\\b")
            .findAll(cleaned)
            .map { it.value.toInt() }
            .firstOrNull { it in 1900..Year.now().value + 1 }
        val venue = lines.firstOrNull { it.looksLikeVenue() }
        val quality = when {
            safeDoi != null || title != null && authors.isNotEmpty() -> ReferenceEvidenceQuality.HIGH
            title != null || authors.isNotEmpty() || year != null -> ReferenceEvidenceQuality.MEDIUM
            cleaned.length >= 24 -> ReferenceEvidenceQuality.LOW
            else -> ReferenceEvidenceQuality.NEEDS_REVIEW
        }
        return ExtractedReferenceEvidence(
            normalizedDoi = safeDoi,
            speculativeDoiRepairs = DoiNormalizer.repairVariants(cleaned),
            title = title,
            authors = authors,
            year = year,
            venue = venue,
            quality = quality,
            needsQueryReview = quality in listOf(ReferenceEvidenceQuality.LOW, ReferenceEvidenceQuality.NEEDS_REVIEW) ||
                titleCandidates.distinctBy { it.normalizeTitle() }.size > 1,
        )
    }

    private fun String.looksLikeTitle(): Boolean {
        val tokens = split(Regex("\\s+")).filter { it.isNotBlank() }
        val letters = count { it.isLetter() }
        val ratio = letters.toDouble() / length.coerceAtLeast(1)
        val upper = count { it.isUpperCase() }.toDouble() / letters.coerceAtLeast(1)
        return tokens.size in 5..24 &&
            ratio >= 0.55 &&
            upper < 0.8 &&
            !endsWith(",") &&
            !contains("doi", ignoreCase = true) &&
            !looksLikeVenue()
    }

    private fun String.titleScore(): Int {
        val tokens = split(Regex("\\s+")).size
        val punctuationPenalty = count { it in listOf(':', ';', ',') }
        return 100 - kotlin.math.abs(tokens - 12) * 3 - punctuationPenalty * 5
    }

    private fun String.looksLikeAuthors(): Boolean {
        val parts = split(Regex("\\s+(?:and|&)\\s+|,|;")).map { it.trim() }.filter { it.isNotBlank() }
        return parts.size in 2..8 && parts.count { it.any(Char::isUpperCase) && it.any(Char::isLetter) } >= 2
    }

    private fun String.looksLikeVenue(): Boolean {
        val lower = lowercase(Locale.US)
        return listOf("journal", "conference", "proceedings", "transactions", "nature", "science", "neurips", "icml", "acl", "cvpr")
            .any { lower.contains(it) }
    }
}

data class ExtractedReferenceEvidence(
    val normalizedDoi: String?,
    val speculativeDoiRepairs: List<DoiRepairCandidate>,
    val title: String?,
    val authors: List<String>,
    val year: Int?,
    val venue: String?,
    val quality: ReferenceEvidenceQuality,
    val needsQueryReview: Boolean,
)

object DoiNormalizer {
    private val doiRegex = Regex("""(?i)\b10\.\d{4,9}\s*/\s*[-._;()/:A-Z0-9\s]+\b""")
    private val repairableDoiRegex = Regex("""(?i)\b1[0o]\.\d{4,9}\s*/\s*[-._;()/:A-Z0-9\s]+\b""")
    private val prefixRegex = Regex("""(?i)^(?:doi:\s*|https?://(?:dx\.)?doi\.org/)""")
    private val trailingRegex = Regex("""[.,;:)\]}>\s]+$""")

    fun extractSafeNormalizedDoi(text: String): String? {
        return doiRegex.find(text)?.value?.let(::normalizeSafe)
    }

    fun normalizeSafe(value: String): String {
        return value.trim()
            .replace(prefixRegex, "")
            .replace(Regex("\\s*/\\s*"), "/")
            .replace(Regex("\\s+"), "")
            .replace(trailingRegex, "")
            .trim('(', '[', '{', '<')
            .lowercase(Locale.US)
    }

    fun repairVariants(text: String): List<DoiRepairCandidate> {
        val candidate = doiRegex.find(text)?.value ?: repairableDoiRegex.find(text)?.value ?: return emptyList()
        val safe = normalizeSafe(candidate)
        val variants = linkedMapOf<String, String>()
        fun add(value: String, reason: String) {
            val normalized = normalizeSafe(value)
            if (normalized != safe) variants.putIfAbsent(normalized, reason)
        }
        add(safe.replace('O', '0').replace('o', '0'), "OCR O/o replaced with 0")
        add(safe.replace('l', '1').replace('I', '1'), "OCR I/l replaced with 1")
        add(safe.replace('–', '-').replace('—', '-'), "OCR dash normalized")
        return variants.map { (doi, reason) -> DoiRepairCandidate(doi, reason) }
    }
}

data class DoiRepairCandidate(
    val normalizedDoi: String,
    val reason: String,
)

fun String.normalizeTitle(): String {
    return lowercase(Locale.US)
        .replace(Regex("[^a-z0-9\\u4e00-\\u9fff ]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}
