package com.yuukias.seminararc.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "ocr_results",
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
            childColumns = ["assetId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("seminarId"),
        Index(value = ["assetId"], unique = true),
    ],
)
data class OcrResultEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val seminarId: Long,
    val assetId: Long,
    val recognizedText: String,
    val editedText: String?,
    val blockJson: String?,
    val languageHint: String?,
    val confidence: Float?,
    val providerId: String,
    val providerVersion: String,
    val isEdited: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
)
