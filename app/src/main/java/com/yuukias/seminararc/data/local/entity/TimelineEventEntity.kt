package com.yuukias.seminararc.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.yuukias.seminararc.domain.model.TimelineEventType
import java.time.Instant

@Entity(
    tableName = "timeline_events",
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
    ],
    indices = [
        Index("seminarId"),
        Index("recordingId"),
        Index(value = ["seminarId", "offsetMs"]),
    ],
)
data class TimelineEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val seminarId: Long,
    val recordingId: Long?,
    val type: TimelineEventType,
    val offsetMs: Long,
    val createdAt: Instant,
    val text: String?,
    val photoPath: String?,
)
