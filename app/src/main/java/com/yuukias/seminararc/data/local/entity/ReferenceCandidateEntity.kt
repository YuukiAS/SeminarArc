package com.yuukias.seminararc.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.yuukias.seminararc.domain.model.ReferenceCandidateStatus
import com.yuukias.seminararc.domain.model.ReferenceConfidenceBand
import java.time.Instant

@Entity(
    tableName = "reference_candidates",
    foreignKeys = [
        ForeignKey(
            entity = SeminarEntity::class,
            parentColumns = ["id"],
            childColumns = ["seminarId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ReferenceLookupAttemptEntity::class,
            parentColumns = ["id"],
            childColumns = ["lookupAttemptId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("seminarId"),
        Index(value = ["seminarId", "normalizedDoi"]),
        Index(value = ["seminarId", "normalizedTitle", "publicationYear"]),
        Index(value = ["seminarId", "status"]),
        Index(value = ["seminarId", "evidenceFingerprint"]),
        Index("lookupAttemptId"),
    ],
)
data class ReferenceCandidateEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
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
