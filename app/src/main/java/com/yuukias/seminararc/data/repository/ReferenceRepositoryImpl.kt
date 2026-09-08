package com.yuukias.seminararc.data.repository

import com.yuukias.seminararc.data.local.dao.ReferenceDao
import com.yuukias.seminararc.data.local.dao.ReconstructionDao
import com.yuukias.seminararc.data.local.entity.BriefKeySlideEntity
import com.yuukias.seminararc.data.local.entity.BriefReferenceEntity
import com.yuukias.seminararc.data.local.entity.ReferenceCandidateEntity
import com.yuukias.seminararc.data.local.entity.ReferenceCandidateSourceEntity
import com.yuukias.seminararc.data.local.entity.ReferenceEvidenceEntity
import com.yuukias.seminararc.data.local.entity.ReferenceLookupAttemptEntity
import com.yuukias.seminararc.data.local.entity.SeminarBriefEntity
import com.yuukias.seminararc.domain.model.BriefKeySlide
import com.yuukias.seminararc.domain.model.BriefReference
import com.yuukias.seminararc.domain.model.REFERENCE_MATCHER_VERSION
import com.yuukias.seminararc.domain.model.ReferenceCandidate
import com.yuukias.seminararc.domain.model.ReferenceCandidateSource
import com.yuukias.seminararc.domain.model.ReferenceCandidateStatus
import com.yuukias.seminararc.domain.model.ReferenceConfidenceBand
import com.yuukias.seminararc.domain.model.ReferenceEvidence
import com.yuukias.seminararc.domain.model.ReferenceEvidenceQuality
import com.yuukias.seminararc.domain.model.ReferenceLookupAttempt
import com.yuukias.seminararc.domain.model.ReferenceLookupAttemptState
import com.yuukias.seminararc.domain.model.ReferenceLookupProviderId
import com.yuukias.seminararc.domain.model.ReferenceLookupQuery
import com.yuukias.seminararc.domain.model.ReferenceLookupQueryType
import com.yuukias.seminararc.domain.model.ReferenceLookupSummary
import com.yuukias.seminararc.domain.model.ReferenceProviderResponse
import com.yuukias.seminararc.domain.model.ReferenceQueryPreview
import com.yuukias.seminararc.domain.model.SeminarAsset
import com.yuukias.seminararc.domain.model.SeminarBrief
import com.yuukias.seminararc.domain.model.SeminarBriefBundle
import com.yuukias.seminararc.domain.reference.DoiNormalizer
import com.yuukias.seminararc.domain.reference.ReferenceEvidenceExtractor
import com.yuukias.seminararc.domain.reference.ReferenceMatcher
import com.yuukias.seminararc.domain.reference.normalizeTitle
import com.yuukias.seminararc.domain.repository.CreateReferenceEvidenceInput
import com.yuukias.seminararc.domain.repository.ReferenceLookupProvider
import com.yuukias.seminararc.domain.repository.ReferenceRepository
import com.yuukias.seminararc.domain.repository.SaveSeminarBriefInput
import com.yuukias.seminararc.util.ClockProvider
import java.security.MessageDigest
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ReferenceRepositoryImpl @Inject constructor(
    private val referenceDao: ReferenceDao,
    private val reconstructionDao: ReconstructionDao,
    private val extractor: ReferenceEvidenceExtractor,
    private val matcher: ReferenceMatcher,
    private val providers: Set<@JvmSuppressWildcards ReferenceLookupProvider>,
    private val clockProvider: ClockProvider,
) : ReferenceRepository {

    override fun observeEvidence(seminarId: Long): Flow<List<ReferenceEvidence>> {
        return referenceDao.observeEvidence(seminarId).map { rows -> rows.map { it.toDomain() } }
    }

    override fun observeCandidates(seminarId: Long): Flow<List<ReferenceCandidate>> {
        return referenceDao.observeCandidates(seminarId).map { rows -> rows.map { it.toDomain() } }
    }

    override fun observeAttempts(seminarId: Long): Flow<List<ReferenceLookupAttempt>> {
        return referenceDao.observeAttempts(seminarId).map { rows -> rows.map { it.toDomain() } }
    }

    override fun observeBrief(seminarId: Long): Flow<SeminarBrief?> {
        return referenceDao.observeBrief(seminarId).map { row -> row?.toDomain() }
    }

    override suspend fun createEvidence(input: CreateReferenceEvidenceInput): ReferenceEvidence {
        val selected = input.selectedText.trim().take(MAX_SELECTED_TEXT_LENGTH)
        require(selected.isNotBlank()) { "Reference evidence must not be blank." }
        val extracted = extractor.extract(selected)
        val id = referenceDao.insertEvidence(
            ReferenceEvidenceEntity(
                seminarId = input.seminarId,
                sourceType = input.sourceType,
                sourceId = input.sourceId,
                sourceAssetId = input.sourceAssetId,
                selectedText = selected,
                extractedDoi = extracted.normalizedDoi,
                titleClue = extracted.title,
                authorCluesJson = Json.encodeToString(extracted.authors),
                yearClue = extracted.year,
                venueClue = extracted.venue,
                evidenceQuality = extracted.quality,
                createdAt = clockProvider.now(),
            ),
        )
        return referenceDao.getEvidence(listOf(id)).first().toDomain()
    }

    override suspend fun buildQueryPreview(
        seminarId: Long,
        evidenceIds: List<Long>,
        manualText: String,
    ): ReferenceQueryPreview? {
        val evidence = referenceDao.getEvidence(evidenceIds.distinct()).filter { it.seminarId == seminarId }
        val manual = manualText.trim()
        if (evidence.isEmpty() && manual.isBlank()) return null
        val combined = buildString {
            evidence.forEach { appendLine(it.selectedText) }
            if (manual.isNotBlank()) appendLine(manual)
        }.trim()
        val extracted = extractor.extract(combined)
        val queryType = if (extracted.normalizedDoi != null) {
            ReferenceLookupQueryType.DOI
        } else {
            ReferenceLookupQueryType.TITLE_AUTHOR_YEAR
        }
        val provider = when {
            queryType == ReferenceLookupQueryType.DOI && combined.contains("dataset", ignoreCase = true) -> ReferenceLookupProviderId.DATACITE
            else -> ReferenceLookupProviderId.CROSSREF
        }
        val query = ReferenceLookupQuery(
            provider = provider,
            queryType = queryType,
            doi = extracted.normalizedDoi,
            title = extracted.title ?: manual.takeIf { it.isNotBlank() },
            authors = extracted.authors,
            year = extracted.year,
            venue = extracted.venue,
        )
        val fingerprint = fingerprint("${seminarId}|${evidence.map { it.id }}|${query.previewText()}")
        return ReferenceQueryPreview(
            evidenceIds = evidence.map { it.id },
            query = query,
            requestFingerprint = fingerprint,
            selectedTextPreview = combined.take(MAX_PREVIEW_LENGTH),
        )
    }

    override suspend fun runLookup(preview: ReferenceQueryPreview): ReferenceLookupSummary {
        val now = clockProvider.now()
        val cachedAttempt = referenceDao.getAttemptByFingerprint(preview.requestFingerprint)
        if (cachedAttempt != null && cachedAttempt.state == ReferenceLookupAttemptState.SUCCEEDED) {
            return ReferenceLookupSummary(listOf(cachedAttempt.toDomain()), emptyList())
        }
        val attempt = cachedAttempt ?: referenceDao.insertAttempt(
            ReferenceLookupAttemptEntity(
                seminarId = inferSeminarId(preview.evidenceIds),
                provider = preview.query.provider,
                queryType = preview.query.queryType,
                evidenceIdsJson = Json.encodeToString(preview.evidenceIds),
                queryPreview = preview.query.previewText(),
                requestFingerprint = preview.requestFingerprint,
                state = ReferenceLookupAttemptState.QUEUED,
                httpStatus = null,
                resultCount = 0,
                cacheHit = false,
                errorMessage = null,
                retryAfterEpochMs = null,
                createdAt = now,
                completedAt = null,
            ),
        ).let { id -> referenceDao.getAttempt(id) ?: error("Attempt $id was not readable after insert.") }
        referenceDao.updateAttempt(attempt.copy(state = ReferenceLookupAttemptState.RUNNING, errorMessage = null))
        val evidence = referenceDao.getEvidence(preview.evidenceIds)
        val plannedProviders = providersFor(preview.query.provider, preview.query.queryType)
        val availableProviders = plannedProviders.mapNotNull { providerId -> providers.firstOrNull { it.id == providerId } }
        if (availableProviders.isEmpty()) {
            return finishAttempt(attempt, ReferenceLookupAttemptState.FAILED, "No reference provider is available.", null, 0)
        }
        val responses = availableProviders.map { provider ->
            provider.lookup(preview.query.copy(provider = provider.id))
        }
        val successfulResults = responses.filterIsInstance<ReferenceProviderResponse.Success>().flatMap { it.results }
        if (successfulResults.isNotEmpty()) {
            val persisted = persistResults(attempt.id, attempt.seminarId, preview, evidence.map { it.toDomain() }, successfulResults)
            referenceDao.updateAttempt(
                attempt.copy(
                    state = ReferenceLookupAttemptState.SUCCEEDED,
                    httpStatus = responses.filterIsInstance<ReferenceProviderResponse.Success>().firstOrNull()?.httpStatus,
                    resultCount = successfulResults.size,
                    completedAt = clockProvider.now(),
                    errorMessage = null,
                ),
            )
            return ReferenceLookupSummary(listOf((referenceDao.getAttempt(attempt.id) ?: attempt).toDomain()), persisted)
        }
        val rateLimited = responses.filterIsInstance<ReferenceProviderResponse.RateLimited>().firstOrNull()
        if (rateLimited != null) {
            return finishAttempt(
                attempt,
                ReferenceLookupAttemptState.RATE_LIMITED,
                rateLimited.message,
                rateLimited.httpStatus,
                0,
                rateLimited.retryAfterEpochMs,
            )
        }
        val failed = responses.filterIsInstance<ReferenceProviderResponse.Failed>().firstOrNull()
        return finishAttempt(
            attempt,
            ReferenceLookupAttemptState.FAILED,
            failed?.message ?: "Reference providers returned no results.",
            failed?.httpStatus,
            0,
        )
    }

    override suspend fun cancelLookup(requestFingerprint: String): ReferenceLookupAttempt? {
        val existing = referenceDao.getAttemptByFingerprint(requestFingerprint) ?: return null
        val updated = existing.copy(
            state = ReferenceLookupAttemptState.CANCELLED,
            completedAt = clockProvider.now(),
        )
        referenceDao.updateAttempt(updated)
        return updated.toDomain()
    }

    override suspend fun reviewCandidate(candidateId: Long, status: ReferenceCandidateStatus): ReferenceCandidate? {
        val existing = referenceDao.getCandidate(candidateId) ?: return null
        val updated = existing.copy(
            status = status,
            updatedAt = clockProvider.now(),
            reviewedAt = clockProvider.now(),
        )
        referenceDao.updateCandidate(updated)
        if (status == ReferenceCandidateStatus.CONFIRMED) {
            val brief = getOrCreateBrief(existing.seminarId)
            setBriefReference(brief.id, existing.id, included = true)
        } else {
            referenceDao.getBrief(existing.seminarId)?.let { brief ->
                setBriefReference(brief.id, existing.id, included = false)
            }
        }
        return updated.toDomain()
    }

    override suspend fun getSourcesForCandidate(candidateId: Long): List<ReferenceCandidateSource> {
        return referenceDao.getSourcesForCandidate(candidateId).map { it.toDomain() }
    }

    override suspend fun getOrCreateBrief(seminarId: Long): SeminarBrief {
        val now = clockProvider.now()
        return referenceDao.getOrCreateBrief(
            SeminarBriefEntity(
                seminarId = seminarId,
                backgroundContext = "",
                coreQuestion = "",
                methods = "",
                mainResults = "",
                keyTakeaways = "",
                unresolvedQuestions = "",
                followUpActions = "",
                userNotes = "",
                createdAt = now,
                updatedAt = now,
            ),
        ).toDomain()
    }

    override suspend fun saveBrief(input: SaveSeminarBriefInput): SeminarBrief? {
        val existing = getOrCreateBrief(input.seminarId)
        val updated = SeminarBriefEntity(
            id = existing.id,
            seminarId = input.seminarId,
            backgroundContext = input.backgroundContext.trim(),
            coreQuestion = input.coreQuestion.trim(),
            methods = input.methods.trim(),
            mainResults = input.mainResults.trim(),
            keyTakeaways = input.keyTakeaways.trim(),
            unresolvedQuestions = input.unresolvedQuestions.trim(),
            followUpActions = input.followUpActions.trim(),
            userNotes = input.userNotes.trim(),
            createdAt = existing.createdAt,
            updatedAt = clockProvider.now(),
        )
        referenceDao.updateBrief(updated)
        return updated.toDomain()
    }

    override suspend fun setBriefReference(
        briefId: Long,
        candidateId: Long,
        included: Boolean,
        note: String?,
    ): Boolean {
        return if (included) {
            val order = referenceDao.getBriefReferences(briefId).size
            referenceDao.upsertBriefReference(BriefReferenceEntity(briefId, candidateId, order, note?.trim()))
            true
        } else {
            referenceDao.deleteBriefReference(briefId, candidateId) > 0
        }
    }

    override suspend fun setBriefKeySlide(
        briefId: Long,
        assetId: Long,
        included: Boolean,
        caption: String?,
    ): Boolean {
        return if (included) {
            val order = referenceDao.getBriefKeySlides(briefId).size
            referenceDao.upsertBriefKeySlide(BriefKeySlideEntity(briefId, assetId, order, caption?.trim()))
            true
        } else {
            referenceDao.deleteBriefKeySlide(briefId, assetId) > 0
        }
    }

    override suspend fun getBriefBundle(seminarId: Long): SeminarBriefBundle? {
        val brief = referenceDao.getBrief(seminarId) ?: return null
        val refs = referenceDao.getBriefReferences(brief.id)
        val candidates = refs.mapNotNull { join ->
            referenceDao.getCandidate(join.referenceCandidateId)
                ?.takeIf { it.status == ReferenceCandidateStatus.CONFIRMED }
                ?.toDomain()
                ?.let { it to join.toDomain() }
        }
        val slides = referenceDao.getBriefKeySlides(brief.id).mapNotNull { join ->
            reconstructionDao.getAsset(join.assetId)?.toDomain()?.let { it to join.toDomain() }
        }
        return SeminarBriefBundle(brief.toDomain(), candidates, slides)
    }

    private suspend fun persistResults(
        attemptId: Long,
        seminarId: Long,
        preview: ReferenceQueryPreview,
        evidence: List<ReferenceEvidence>,
        results: List<com.yuukias.seminararc.domain.model.ReferenceProviderResult>,
    ): List<ReferenceCandidate> {
        val groups = results.groupBy { result ->
            result.doi?.let { "doi:${DoiNormalizer.normalizeSafe(it)}" }
                ?: "title:${result.title.normalizeTitle()}|${result.publicationYear}|${result.authors.firstOrNull()?.lowercase()}"
        }
        return groups.mapNotNull { (_, observations) ->
            val primary = observations.maxByOrNull { it.providerRawScore ?: 0.0 } ?: return@mapNotNull null
            val match = matcher.score(preview.query, evidence, primary, observations.map { it.provider }.distinct().size)
            val normalizedDoi = primary.doi?.let(DoiNormalizer::normalizeSafe)
            val normalizedTitle = primary.title.normalizeTitle()
            val existing = normalizedDoi?.let { referenceDao.getCandidateByDoi(seminarId, it) }
                ?: referenceDao.getCandidateByTitleYear(seminarId, normalizedTitle, primary.publicationYear)
            val now = clockProvider.now()
            val candidate = if (existing == null) {
                val entity = ReferenceCandidateEntity(
                    seminarId = seminarId,
                    canonicalDoi = primary.doi,
                    normalizedDoi = normalizedDoi,
                    title = primary.title,
                    normalizedTitle = normalizedTitle,
                    authorsJson = Json.encodeToString(primary.authors),
                    publicationYear = primary.publicationYear,
                    venue = primary.venue,
                    sourceTitle = primary.sourceTitle,
                    publicationType = primary.publicationType,
                    landingPageUrl = primary.landingPageUrl,
                    openAccessUrl = primary.openAccessUrl,
                    licenseUrl = primary.licenseUrl,
                    matcherVersion = match.matcherVersion,
                    matchScore = match.score,
                    confidenceBand = match.confidenceBand,
                    matchReasonsJson = matcher.reasonJson(match.reasons),
                    status = ReferenceCandidateStatus.PENDING,
                    evidenceFingerprint = preview.requestFingerprint,
                    lookupAttemptId = attemptId,
                    createdAt = now,
                    updatedAt = now,
                    reviewedAt = null,
                )
                val id = referenceDao.insertCandidate(entity)
                referenceDao.getCandidate(id) ?: entity.copy(id = id)
            } else if (existing.status == ReferenceCandidateStatus.PENDING) {
                val updated = existing.copy(
                    matchScore = maxOf(existing.matchScore, match.score),
                    confidenceBand = strongest(existing.confidenceBand, match.confidenceBand),
                    matchReasonsJson = matcher.reasonJson(match.reasons),
                    lookupAttemptId = attemptId,
                    updatedAt = now,
                )
                referenceDao.updateCandidate(updated)
                updated
            } else {
                existing
            }
            observations.forEach { result ->
                referenceDao.insertSource(result.toSourceEntity(candidate.id, now))
            }
            candidate.toDomain()
        }
    }

    private fun providersFor(
        primary: ReferenceLookupProviderId,
        queryType: ReferenceLookupQueryType,
    ): List<ReferenceLookupProviderId> {
        return when (primary) {
            ReferenceLookupProviderId.DATACITE -> listOf(ReferenceLookupProviderId.DATACITE)
            ReferenceLookupProviderId.OPENALEX -> listOf(ReferenceLookupProviderId.OPENALEX)
            ReferenceLookupProviderId.CROSSREF -> {
                if (queryType == ReferenceLookupQueryType.DOI) {
                    listOf(ReferenceLookupProviderId.CROSSREF, ReferenceLookupProviderId.OPENALEX, ReferenceLookupProviderId.DATACITE)
                } else {
                    listOf(ReferenceLookupProviderId.CROSSREF, ReferenceLookupProviderId.OPENALEX)
                }
            }
        }
    }

    private suspend fun finishAttempt(
        attempt: ReferenceLookupAttemptEntity,
        state: ReferenceLookupAttemptState,
        message: String?,
        httpStatus: Int?,
        resultCount: Int,
        retryAfterEpochMs: Long? = null,
    ): ReferenceLookupSummary {
        val updated = attempt.copy(
            state = state,
            httpStatus = httpStatus,
            resultCount = resultCount,
            errorMessage = message,
            retryAfterEpochMs = retryAfterEpochMs,
            completedAt = clockProvider.now(),
        )
        referenceDao.updateAttempt(updated)
        return ReferenceLookupSummary(listOf(updated.toDomain()), emptyList())
    }

    private suspend fun inferSeminarId(evidenceIds: List<Long>): Long {
        return referenceDao.getEvidence(evidenceIds).firstOrNull()?.seminarId
            ?: error("Reference lookup requires persisted seminar evidence.")
    }

    private fun strongest(a: ReferenceConfidenceBand, b: ReferenceConfidenceBand): ReferenceConfidenceBand {
        return listOf(a, b).minBy { it.ordinal }
    }

    private fun com.yuukias.seminararc.domain.model.ReferenceProviderResult.toSourceEntity(
        candidateId: Long,
        now: java.time.Instant,
    ): ReferenceCandidateSourceEntity {
        return ReferenceCandidateSourceEntity(
            candidateId = candidateId,
            provider = provider,
            providerWorkId = providerWorkId,
            doi = doi,
            normalizedDoi = doi?.let(DoiNormalizer::normalizeSafe),
            title = title,
            normalizedTitle = title.normalizeTitle(),
            authorsJson = Json.encodeToString(authors),
            publicationYear = publicationYear,
            venue = venue,
            sourceTitle = sourceTitle,
            publicationType = publicationType,
            landingPageUrl = landingPageUrl,
            openAccessUrl = openAccessUrl,
            licenseUrl = licenseUrl,
            providerRawScore = providerRawScore,
            providerPayloadJson = providerPayloadJson?.take(MAX_PAYLOAD_LENGTH),
            metadataVersion = "selected-fields-v1",
            fetchedAt = now,
        )
    }

    private fun ReferenceEvidenceEntity.toDomain(): ReferenceEvidence {
        return ReferenceEvidence(id, seminarId, sourceType, sourceId, sourceAssetId, selectedText, extractedDoi, titleClue, authorCluesJson, yearClue, venueClue, evidenceQuality, createdAt)
    }

    private fun ReferenceLookupAttemptEntity.toDomain(): ReferenceLookupAttempt {
        return ReferenceLookupAttempt(id, seminarId, provider, queryType, evidenceIdsJson, queryPreview, requestFingerprint, state, httpStatus, resultCount, cacheHit, errorMessage, retryAfterEpochMs, createdAt, completedAt)
    }

    private fun ReferenceCandidateEntity.toDomain(): ReferenceCandidate {
        return ReferenceCandidate(id, seminarId, canonicalDoi, normalizedDoi, title, normalizedTitle, authorsJson, publicationYear, venue, sourceTitle, publicationType, landingPageUrl, openAccessUrl, licenseUrl, matcherVersion, matchScore, confidenceBand, matchReasonsJson, status, evidenceFingerprint, lookupAttemptId, createdAt, updatedAt, reviewedAt)
    }

    private fun ReferenceCandidateSourceEntity.toDomain(): ReferenceCandidateSource {
        return ReferenceCandidateSource(id, candidateId, provider, providerWorkId, doi, normalizedDoi, title, normalizedTitle, authorsJson, publicationYear, venue, sourceTitle, publicationType, landingPageUrl, openAccessUrl, licenseUrl, providerRawScore, providerPayloadJson, metadataVersion, fetchedAt)
    }

    private fun SeminarBriefEntity.toDomain(): SeminarBrief {
        return SeminarBrief(id, seminarId, backgroundContext, coreQuestion, methods, mainResults, keyTakeaways, unresolvedQuestions, followUpActions, userNotes, createdAt, updatedAt)
    }

    private fun BriefReferenceEntity.toDomain(): BriefReference = BriefReference(briefId, referenceCandidateId, orderIndex, note)

    private fun BriefKeySlideEntity.toDomain(): BriefKeySlide = BriefKeySlide(briefId, assetId, orderIndex, caption)

    private fun com.yuukias.seminararc.data.local.entity.SeminarAssetEntity.toDomain(): SeminarAsset {
        return SeminarAsset(id, seminarId, type, originAssetId, sourceTimelineEventId, sourceRecordingId, sourceClipId, relativePath, mimeType, displayName, createdAt, updatedAt)
    }

    private fun fingerprint(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val MAX_SELECTED_TEXT_LENGTH = 4_000
        const val MAX_PREVIEW_LENGTH = 1_000
        const val MAX_PAYLOAD_LENGTH = 2_000
    }
}
