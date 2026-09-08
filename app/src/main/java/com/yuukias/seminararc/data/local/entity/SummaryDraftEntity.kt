package com.yuukias.seminararc.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.yuukias.seminararc.domain.model.SummaryDraftState
import java.time.Instant

@Entity(
    tableName = "summary_drafts",
    foreignKeys = [
        ForeignKey(
            entity = SeminarEntity::class,
            parentColumns = ["id"],
            childColumns = ["seminarId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("seminarId"),
        Index(value = ["seminarId", "inputFingerprint"], unique = true),
        Index(value = ["seminarId", "state"]),
    ],
)
data class SummaryDraftEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val seminarId: Long,
    val providerId: String,
    val inputFingerprint: String,
    val state: SummaryDraftState,
    val backgroundContext: String,
    val coreQuestion: String,
    val methods: String,
    val mainResults: String,
    val keyTakeaways: String,
    val unresolvedQuestions: String,
    val followUpActions: String,
    val userNotes: String,
    val provenanceJson: String,
    val errorMessage: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
