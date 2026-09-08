package com.yuukias.seminararc.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.yuukias.seminararc.domain.model.TranscriptLanguageHint
import com.yuukias.seminararc.domain.model.TranscriptSourceType
import com.yuukias.seminararc.domain.model.TranscriptState
import java.time.Instant

@Entity(
    tableName = "transcripts",
    foreignKeys = [
        ForeignKey(
            entity = SeminarEntity::class,
            parentColumns = ["id"],
            childColumns = ["seminarId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = RecordingEntity::class,
            parentColumns = ["id"],
            childColumns = ["recordingId"],
            onDelete = ForeignKey.SET_NULL,
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
        Index("recordingId"),
        Index("sourceAssetId"),
        Index(value = ["seminarId", "recordingId", "providerId"]),
        Index(value = ["seminarId", "state"]),
    ],
)
data class TranscriptEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val seminarId: Long,
    val recordingId: Long?,
    val providerId: String,
    val providerVersion: String,
    val languageHint: TranscriptLanguageHint,
    val state: TranscriptState,
    val sourceType: TranscriptSourceType,
    val sourceAssetId: Long?,
    val errorMessage: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
