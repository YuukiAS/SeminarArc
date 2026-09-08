package com.yuukias.seminararc.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "brief_key_slides",
    primaryKeys = ["briefId", "assetId"],
    foreignKeys = [
        ForeignKey(
            entity = SeminarBriefEntity::class,
            parentColumns = ["id"],
            childColumns = ["briefId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = SeminarAssetEntity::class,
            parentColumns = ["id"],
            childColumns = ["assetId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("briefId"),
        Index("assetId"),
    ],
)
data class BriefKeySlideEntity(
    val briefId: Long,
    val assetId: Long,
    val orderIndex: Int,
    val caption: String?,
)
