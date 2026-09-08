package com.yuukias.seminararc.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.yuukias.seminararc.domain.model.ReferenceEvidenceQuality
import com.yuukias.seminararc.domain.model.ReferenceEvidenceSourceType
import java.time.Instant

@Entity(
    tableName = "reference_evidence",
    foreignKeys = [
        ForeignKey(
            entity = SeminarEntity::class,
            parentColumns = ["id"],
            childColumns = ["seminarId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = SeminarAssetEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceAssetId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("seminarId"),
        Index("sourceType"),
        Index("sourceId"),
        Index("sourceAssetId"),
    ],
)
data class ReferenceEvidenceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
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
