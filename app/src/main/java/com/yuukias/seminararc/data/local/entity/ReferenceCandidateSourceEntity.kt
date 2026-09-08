package com.yuukias.seminararc.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.yuukias.seminararc.domain.model.ReferenceLookupProviderId
import java.time.Instant

@Entity(
    tableName = "reference_candidate_sources",
    foreignKeys = [
        ForeignKey(
            entity = ReferenceCandidateEntity::class,
            parentColumns = ["id"],
            childColumns = ["candidateId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("candidateId"),
        Index("provider"),
        Index(value = ["candidateId", "provider", "providerWorkId"], unique = true),
        Index(value = ["candidateId", "normalizedDoi"]),
    ],
)
data class ReferenceCandidateSourceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
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
