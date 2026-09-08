package com.yuukias.seminararc.domain.model

import java.time.Instant

const val REFERENCE_MATCHER_VERSION = "reference-match-v1"

enum class ReferenceEvidenceSourceType {
    OCR_RESULT,
    ASSET,
    TIMELINE_EVENT,
    QUESTION,
    NOTE,
    SEMINAR_METADATA,
    USER_INPUT,
}

enum class ReferenceEvidenceQuality {
    HIGH,
    MEDIUM,
    LOW,
    NEEDS_REVIEW,
}

enum class ReferenceLookupProviderId {
    CROSSREF,
    OPENALEX,
    DATACITE,
}

enum class ReferenceLookupQueryType {
    DOI,
    BIBLIOGRAPHIC,
    TITLE_AUTHOR_YEAR,
}

enum class ReferenceLookupAttemptState {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    RATE_LIMITED,
}

enum class ReferenceConfidenceBand {
    HIGH_CONFIDENCE,
    POSSIBLE_MATCH,
    NEEDS_REVIEW,
}

enum class ReferenceCandidateStatus {
    PENDING,
    CONFIRMED,
    REJECTED,
}

data class ReferenceEvidence(
    val id: Long,
    val seminarId: Long,
    val sourceType: ReferenceEvidenceSourceType,
    val sourceId: Long?,
    val sourceAssetId: Long?,
    val selectedText: String,
    val extractedDoi: String?,
    val titleClue: String?,
    val authorCluesJson: String?,
    val yearClue: Int?,
    val venueClue: String?,
    val evidenceQuality: ReferenceEvidenceQuality,
    val createdAt: Instant,
)

data class ReferenceLookupAttempt(
    val id: Long,
    val seminarId: Long,
    val provider: ReferenceLookupProviderId,
    val queryType: ReferenceLookupQueryType,
    val evidenceIdsJson: String,
    val queryPreview: String,
    val requestFingerprint: String,
    val state: ReferenceLookupAttemptState,
    val httpStatus: Int?,
    val resultCount: Int,
    val cacheHit: Boolean,
    val errorMessage: String?,
    val retryAfterEpochMs: Long?,
    val createdAt: Instant,
    val completedAt: Instant?,
)

data class ReferenceCandidate(
    val id: Long,
    val seminarId: Long,
    val canonicalDoi: String?,
    val normalizedDoi: String?,
    val title: String,
    val normalizedTitle: String,
    val authorsJson: String,
    val publicationYear: Int?,
    val venue: String?,
    val sourceTitle: String?,
    val publicationType: String?,
    val landingPageUrl: String?,
    val openAccessUrl: String?,
    val licenseUrl: String?,
    val matcherVersion: String,
    val matchScore: Int,
    val confidenceBand: ReferenceConfidenceBand,
    val matchReasonsJson: String,
    val status: ReferenceCandidateStatus,
    val evidenceFingerprint: String,
    val lookupAttemptId: Long?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val reviewedAt: Instant?,
)

data class ReferenceCandidateSource(
    val id: Long,
    val candidateId: Long,
    val provider: ReferenceLookupProviderId,
    val providerWorkId: String?,
    val doi: String?,
    val normalizedDoi: String?,
    val title: String?,
    val normalizedTitle: String?,
    val authorsJson: String,
    val publicationYear: Int?,
    val venue: String?,
    val sourceTitle: String?,
    val publicationType: String?,
    val landingPageUrl: String?,
    val openAccessUrl: String?,
    val licenseUrl: String?,
    val providerRawScore: Double?,
    val providerPayloadJson: String?,
    val metadataVersion: String,
    val fetchedAt: Instant,
)

data class SeminarBrief(
    val id: Long,
    val seminarId: Long,
    val backgroundContext: String,
    val coreQuestion: String,
    val methods: String,
    val mainResults: String,
    val keyTakeaways: String,
    val unresolvedQuestions: String,
    val followUpActions: String,
    val userNotes: String,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class BriefReference(
    val briefId: Long,
    val referenceCandidateId: Long,
    val orderIndex: Int,
    val note: String?,
)

data class BriefKeySlide(
    val briefId: Long,
    val assetId: Long,
    val orderIndex: Int,
    val caption: String?,
)

data class ReferenceLookupQuery(
    val provider: ReferenceLookupProviderId,
    val queryType: ReferenceLookupQueryType,
    val doi: String? = null,
    val title: String? = null,
    val authors: List<String> = emptyList(),
    val year: Int? = null,
    val venue: String? = null,
) {
    fun previewText(): String = buildList {
        doi?.let { add("DOI: $it") }
        title?.let { add("Title: $it") }
        if (authors.isNotEmpty()) add("Authors: ${authors.joinToString(", ")}")
        year?.let { add("Year: $it") }
        venue?.let { add("Venue: $it") }
        add("Provider: ${provider.name}")
    }.joinToString("\n")
}

data class ReferenceProviderResult(
    val provider: ReferenceLookupProviderId,
    val providerWorkId: String?,
    val doi: String?,
    val title: String,
    val authors: List<String>,
    val publicationYear: Int?,
    val venue: String?,
    val sourceTitle: String?,
    val publicationType: String?,
    val landingPageUrl: String?,
    val openAccessUrl: String?,
    val licenseUrl: String?,
    val providerRawScore: Double?,
    val providerPayloadJson: String?,
)

sealed interface ReferenceProviderResponse {
    data class Success(val results: List<ReferenceProviderResult>, val httpStatus: Int? = null) : ReferenceProviderResponse
    data class Failed(val message: String, val httpStatus: Int? = null, val retryable: Boolean = true) : ReferenceProviderResponse
    data class RateLimited(val message: String, val retryAfterEpochMs: Long?, val httpStatus: Int? = 429) : ReferenceProviderResponse
}

data class ReferenceQueryPreview(
    val evidenceIds: List<Long>,
    val query: ReferenceLookupQuery,
    val requestFingerprint: String,
    val selectedTextPreview: String,
)

data class ReferenceLookupSummary(
    val attempts: List<ReferenceLookupAttempt>,
    val candidates: List<ReferenceCandidate>,
)

data class SeminarBriefBundle(
    val brief: SeminarBrief,
    val references: List<Pair<ReferenceCandidate, BriefReference>>,
    val keySlides: List<Pair<SeminarAsset, BriefKeySlide>>,
)
