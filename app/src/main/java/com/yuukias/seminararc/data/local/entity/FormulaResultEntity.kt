package com.yuukias.seminararc.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.yuukias.seminararc.domain.model.FormulaResultState
import java.time.Instant

@Entity(
    tableName = "formula_results",
    foreignKeys = [
        ForeignKey(
            entity = SeminarEntity::class,
            parentColumns = ["id"],
            childColumns = ["seminarId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = FormulaRegionEntity::class,
            parentColumns = ["id"],
            childColumns = ["regionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("seminarId"),
        Index("regionId"),
        Index(value = ["seminarId", "regionId"]),
        Index(value = ["seminarId", "state"]),
    ],
)
data class FormulaResultEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val seminarId: Long,
    val regionId: Long,
    val providerId: String,
    val providerVersion: String,
    val state: FormulaResultState,
    val latex: String,
    val confidence: Float?,
    val isEdited: Boolean,
    val errorMessage: String?,
    val provenanceJson: String,
    val createdAt: Instant,
    val updatedAt: Instant,
)
