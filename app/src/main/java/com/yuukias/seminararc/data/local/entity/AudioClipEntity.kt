package com.yuukias.seminararc.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.yuukias.seminararc.domain.model.ClipState

@Entity(
    tableName = "audio_clips",
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
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TimelineEventEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceEventId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("seminarId"),
        Index("recordingId"),
        Index("sourceEventId"),
    ],
)
data class AudioClipEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val seminarId: Long,
    val recordingId: Long,
    val sourceEventId: Long,
    val startOffsetMs: Long,
    val endOffsetMs: Long,
    val filePath: String?,
    val state: ClipState,
    val errorMessage: String?,
    val retryCount: Int = 0,
)
