package com.yuukias.seminararc.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "tags",
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
        Index(value = ["key"], unique = true),
    ],
)
data class TagEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val seminarId: Long?,
    val key: String,
    val label: String,
    val isSystem: Boolean,
    val createdAt: Instant,
)
