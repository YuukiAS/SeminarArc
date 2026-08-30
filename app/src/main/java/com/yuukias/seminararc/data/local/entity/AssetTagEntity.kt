package com.yuukias.seminararc.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "asset_tags",
    primaryKeys = ["assetId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = SeminarAssetEntity::class,
            parentColumns = ["id"],
            childColumns = ["assetId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("assetId"),
        Index("tagId"),
    ],
)
data class AssetTagEntity(
    val assetId: Long,
    val tagId: Long,
)
