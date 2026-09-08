package com.yuukias.seminararc.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "transcript_segments",
    foreignKeys = [
        ForeignKey(
            entity = TranscriptEntity::class,
            parentColumns = ["id"],
            childColumns = ["transcriptId"],
            onDelete = ForeignKey.CASCADE,
        ),
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
    ],
    indices = [
        Index("transcriptId"),
        Index("seminarId"),
        Index("recordingId"),
        Index(value = ["seminarId", "startOffsetMs"]),
        Index(value = ["transcriptId", "startOffsetMs"]),
    ],
)
data class TranscriptSegmentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val transcriptId: Long,
    val seminarId: Long,
    val recordingId: Long?,
    val startOffsetMs: Long,
    val endOffsetMs: Long,
    val speakerLabel: String?,
    val language: String?,
    val text: String,
    val confidence: Float?,
    val isEdited: Boolean,
    val providerSegmentId: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
