package com.yuukias.seminararc.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.yuukias.seminararc.domain.model.SeminarStatus
import java.time.Instant

@Entity(tableName = "seminars")
data class SeminarEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val title: String,
    val speaker: String?,
    val affiliation: String?,
    val scheduledAt: Instant?,
    val location: String?,
    val abstractText: String?,
    val abstractPdfPath: String?,
    val status: SeminarStatus,
    val rating: Int?,
    val isFavorite: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
    val sessionStartedAt: Instant?,
    val sessionEndedAt: Instant?,
)
