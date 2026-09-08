package com.yuukias.seminararc.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "seminar_briefs",
    foreignKeys = [
        ForeignKey(
            entity = SeminarEntity::class,
            parentColumns = ["id"],
            childColumns = ["seminarId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["seminarId"], unique = true),
    ],
)
data class SeminarBriefEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val seminarId: Long,
    val backgroundContext: String,
    val coreQuestion: String,
    val methods: String,
    val mainResults: String,
    val keyTakeaways: String,
    val unresolvedQuestions: String,
    val followUpActions: String,
    val userNotes: String,
    val createdAt: Instant,
    val updatedAt: Instant,
)
