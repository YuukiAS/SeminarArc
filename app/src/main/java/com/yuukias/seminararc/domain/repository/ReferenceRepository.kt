package com.yuukias.seminararc.domain.repository

import com.yuukias.seminararc.domain.model.BriefKeySlide
import com.yuukias.seminararc.domain.model.BriefReference
import com.yuukias.seminararc.domain.model.ReferenceCandidate
import com.yuukias.seminararc.domain.model.ReferenceCandidateSource
import com.yuukias.seminararc.domain.model.ReferenceCandidateStatus
import com.yuukias.seminararc.domain.model.ReferenceEvidence
import com.yuukias.seminararc.domain.model.ReferenceEvidenceSourceType
import com.yuukias.seminararc.domain.model.ReferenceLookupAttempt
import com.yuukias.seminararc.domain.model.ReferenceLookupQuery
import com.yuukias.seminararc.domain.model.ReferenceLookupSummary
import com.yuukias.seminararc.domain.model.ReferenceQueryPreview
import com.yuukias.seminararc.domain.model.SeminarBrief
import com.yuukias.seminararc.domain.model.SeminarBriefBundle
import kotlinx.coroutines.flow.Flow

interface ReferenceRepository {
    fun observeEvidence(seminarId: Long): Flow<List<ReferenceEvidence>>

    fun observeCandidates(seminarId: Long): Flow<List<ReferenceCandidate>>

    fun observeAttempts(seminarId: Long): Flow<List<ReferenceLookupAttempt>>

    fun observeBrief(seminarId: Long): Flow<SeminarBrief?>

    suspend fun createEvidence(input: CreateReferenceEvidenceInput): ReferenceEvidence

    suspend fun buildQueryPreview(seminarId: Long, evidenceIds: List<Long>, manualText: String): ReferenceQueryPreview?

    suspend fun runLookup(preview: ReferenceQueryPreview): ReferenceLookupSummary

    suspend fun cancelLookup(requestFingerprint: String): ReferenceLookupAttempt?

    suspend fun reviewCandidate(candidateId: Long, status: ReferenceCandidateStatus): ReferenceCandidate?

    suspend fun getSourcesForCandidate(candidateId: Long): List<ReferenceCandidateSource>

    suspend fun getOrCreateBrief(seminarId: Long): SeminarBrief

    suspend fun saveBrief(input: SaveSeminarBriefInput): SeminarBrief?

    suspend fun setBriefReference(briefId: Long, candidateId: Long, included: Boolean, note: String? = null): Boolean

    suspend fun setBriefKeySlide(briefId: Long, assetId: Long, included: Boolean, caption: String? = null): Boolean

    suspend fun getBriefBundle(seminarId: Long): SeminarBriefBundle?
}

data class CreateReferenceEvidenceInput(
    val seminarId: Long,
    val sourceType: ReferenceEvidenceSourceType,
    val sourceId: Long?,
    val sourceAssetId: Long?,
    val selectedText: String,
)

data class SaveSeminarBriefInput(
    val seminarId: Long,
    val backgroundContext: String,
    val coreQuestion: String,
    val methods: String,
    val mainResults: String,
    val keyTakeaways: String,
    val unresolvedQuestions: String,
    val followUpActions: String,
    val userNotes: String,
)

interface ReferenceLookupProvider {
    val id: com.yuukias.seminararc.domain.model.ReferenceLookupProviderId
    suspend fun lookup(query: ReferenceLookupQuery): com.yuukias.seminararc.domain.model.ReferenceProviderResponse
}
