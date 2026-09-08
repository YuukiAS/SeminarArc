package com.yuukias.seminararc.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "brief_references",
    primaryKeys = ["briefId", "referenceCandidateId"],
    foreignKeys = [
        ForeignKey(
            entity = SeminarBriefEntity::class,
            parentColumns = ["id"],
            childColumns = ["briefId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ReferenceCandidateEntity::class,
            parentColumns = ["id"],
            childColumns = ["referenceCandidateId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("briefId"),
        Index("referenceCandidateId"),
    ],
)
data class BriefReferenceEntity(
    val briefId: Long,
    val referenceCandidateId: Long,
    val orderIndex: Int,
    val note: String?,
)
