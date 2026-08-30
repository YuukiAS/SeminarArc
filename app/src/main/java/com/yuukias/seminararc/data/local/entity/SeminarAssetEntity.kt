package com.yuukias.seminararc.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.yuukias.seminararc.domain.model.SeminarAssetType
import java.time.Instant

@Entity(
    tableName = "seminar_assets",
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
            childColumns = ["originAssetId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = TimelineEventEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceTimelineEventId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = RecordingEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceRecordingId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = AudioClipEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceClipId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("seminarId"),
        Index("type"),
        Index("originAssetId"),
        Index("sourceTimelineEventId"),
        Index("sourceRecordingId"),
        Index("sourceClipId"),
        Index(value = ["relativePath"], unique = true),
    ],
)
data class SeminarAssetEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val seminarId: Long,
    val type: SeminarAssetType,
    val originAssetId: Long?,
    val sourceTimelineEventId: Long?,
    val sourceRecordingId: Long?,
    val sourceClipId: Long?,
    val relativePath: String?,
    val mimeType: String?,
    val displayName: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
