package com.yuukias.seminararc.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.yuukias.seminararc.data.local.entity.BriefKeySlideEntity
import com.yuukias.seminararc.data.local.entity.BriefReferenceEntity
import com.yuukias.seminararc.data.local.entity.ReferenceCandidateEntity
import com.yuukias.seminararc.data.local.entity.ReferenceCandidateSourceEntity
import com.yuukias.seminararc.data.local.entity.ReferenceEvidenceEntity
import com.yuukias.seminararc.data.local.entity.ReferenceLookupAttemptEntity
import com.yuukias.seminararc.data.local.entity.SeminarBriefEntity
import com.yuukias.seminararc.domain.model.ReferenceCandidateStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface ReferenceDao {
    @Query("SELECT * FROM reference_evidence WHERE seminarId = :seminarId ORDER BY createdAt DESC, id DESC")
    fun observeEvidence(seminarId: Long): Flow<List<ReferenceEvidenceEntity>>

    @Query("SELECT * FROM reference_candidates WHERE seminarId = :seminarId ORDER BY status ASC, matchScore DESC, updatedAt DESC")
    fun observeCandidates(seminarId: Long): Flow<List<ReferenceCandidateEntity>>

    @Query("SELECT * FROM reference_lookup_attempts WHERE seminarId = :seminarId ORDER BY createdAt DESC, id DESC")
    fun observeAttempts(seminarId: Long): Flow<List<ReferenceLookupAttemptEntity>>

    @Query("SELECT * FROM seminar_briefs WHERE seminarId = :seminarId LIMIT 1")
    fun observeBrief(seminarId: Long): Flow<SeminarBriefEntity?>

    @Query("SELECT * FROM reference_evidence WHERE id IN (:ids) ORDER BY createdAt ASC, id ASC")
    suspend fun getEvidence(ids: List<Long>): List<ReferenceEvidenceEntity>

    @Query("SELECT * FROM reference_lookup_attempts WHERE requestFingerprint = :fingerprint LIMIT 1")
    suspend fun getAttemptByFingerprint(fingerprint: String): ReferenceLookupAttemptEntity?

    @Query("SELECT * FROM reference_lookup_attempts WHERE id = :attemptId")
    suspend fun getAttempt(attemptId: Long): ReferenceLookupAttemptEntity?

    @Query("SELECT * FROM reference_candidates WHERE id = :candidateId")
    suspend fun getCandidate(candidateId: Long): ReferenceCandidateEntity?

    @Query("SELECT * FROM reference_candidates WHERE seminarId = :seminarId AND normalizedDoi = :normalizedDoi LIMIT 1")
    suspend fun getCandidateByDoi(seminarId: Long, normalizedDoi: String): ReferenceCandidateEntity?

    @Query(
        """
        SELECT * FROM reference_candidates
        WHERE seminarId = :seminarId
            AND normalizedTitle = :normalizedTitle
            AND (
                (publicationYear IS NULL AND :publicationYear IS NULL)
                OR publicationYear = :publicationYear
            )
        ORDER BY updatedAt DESC
        LIMIT 1
        """
    )
    suspend fun getCandidateByTitleYear(
        seminarId: Long,
        normalizedTitle: String,
        publicationYear: Int?,
    ): ReferenceCandidateEntity?

    @Query("SELECT * FROM reference_candidates WHERE seminarId = :seminarId AND status = :status ORDER BY matchScore DESC, title ASC")
    suspend fun getCandidatesByStatus(
        seminarId: Long,
        status: ReferenceCandidateStatus,
    ): List<ReferenceCandidateEntity>

    @Query("SELECT * FROM reference_candidate_sources WHERE candidateId = :candidateId ORDER BY fetchedAt DESC, id DESC")
    suspend fun getSourcesForCandidate(candidateId: Long): List<ReferenceCandidateSourceEntity>

    @Query("SELECT * FROM reference_candidate_sources WHERE candidateId IN (:candidateIds) ORDER BY fetchedAt DESC, id DESC")
    suspend fun getSourcesForCandidates(candidateIds: List<Long>): List<ReferenceCandidateSourceEntity>

    @Query("SELECT * FROM seminar_briefs WHERE seminarId = :seminarId LIMIT 1")
    suspend fun getBrief(seminarId: Long): SeminarBriefEntity?

    @Query("SELECT * FROM seminar_briefs WHERE id = :briefId LIMIT 1")
    suspend fun getBriefById(briefId: Long): SeminarBriefEntity?

    @Query("SELECT * FROM brief_references WHERE briefId = :briefId ORDER BY orderIndex ASC, referenceCandidateId ASC")
    suspend fun getBriefReferences(briefId: Long): List<BriefReferenceEntity>

    @Query("SELECT * FROM brief_key_slides WHERE briefId = :briefId ORDER BY orderIndex ASC, assetId ASC")
    suspend fun getBriefKeySlides(briefId: Long): List<BriefKeySlideEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEvidence(entity: ReferenceEvidenceEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAttempt(entity: ReferenceLookupAttemptEntity): Long

    @Update
    suspend fun updateAttempt(entity: ReferenceLookupAttemptEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCandidate(entity: ReferenceCandidateEntity): Long

    @Update
    suspend fun updateCandidate(entity: ReferenceCandidateEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSource(entity: ReferenceCandidateSourceEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBrief(entity: SeminarBriefEntity): Long

    @Update
    suspend fun updateBrief(entity: SeminarBriefEntity)

    @Upsert
    suspend fun upsertBriefReference(entity: BriefReferenceEntity)

    @Upsert
    suspend fun upsertBriefKeySlide(entity: BriefKeySlideEntity)

    @Query("DELETE FROM brief_references WHERE briefId = :briefId AND referenceCandidateId = :candidateId")
    suspend fun deleteBriefReference(briefId: Long, candidateId: Long): Int

    @Query("DELETE FROM brief_key_slides WHERE briefId = :briefId AND assetId = :assetId")
    suspend fun deleteBriefKeySlide(briefId: Long, assetId: Long): Int

    @Transaction
    suspend fun getOrCreateBrief(entity: SeminarBriefEntity): SeminarBriefEntity {
        val existing = getBrief(entity.seminarId)
        if (existing != null) return existing
        val id = insertBrief(entity)
        return getBriefById(id) ?: entity.copy(id = id)
    }
}
