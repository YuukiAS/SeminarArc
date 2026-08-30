package com.yuukias.seminararc.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.yuukias.seminararc.domain.model.ProcessingJobState
import com.yuukias.seminararc.domain.model.ProcessingJobType
import java.time.Instant

@Entity(
    tableName = "processing_jobs",
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
            childColumns = ["inputAssetId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = SeminarAssetEntity::class,
            parentColumns = ["id"],
            childColumns = ["outputAssetId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("seminarId"),
        Index("type"),
        Index("state"),
        Index("inputAssetId"),
        Index("outputAssetId"),
    ],
)
data class ProcessingJobEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val seminarId: Long,
    val type: ProcessingJobType,
    val state: ProcessingJobState,
    val inputAssetId: Long,
    val outputAssetId: Long?,
    val providerId: String,
    val providerVersion: String,
    val createdAt: Instant,
    val startedAt: Instant?,
    val completedAt: Instant?,
    val retryCount: Int,
    val isRetryable: Boolean,
    val errorMessage: String?,
)
