package com.yuukias.seminararc.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.yuukias.seminararc.domain.model.ReferenceLookupAttemptState
import com.yuukias.seminararc.domain.model.ReferenceLookupProviderId
import com.yuukias.seminararc.domain.model.ReferenceLookupQueryType
import java.time.Instant

@Entity(
    tableName = "reference_lookup_attempts",
    foreignKeys = [
        ForeignKey(
            entity = SeminarEntity::class,
            parentColumns = ["id"],
            childColumns = ["seminarId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("seminarId"),
        Index("provider"),
        Index("state"),
        Index(value = ["requestFingerprint"], unique = true),
    ],
)
data class ReferenceLookupAttemptEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
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
