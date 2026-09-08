package com.yuukias.seminararc.domain.model

import java.time.Instant

data class FormulaRegion(
    val id: Long,
    val seminarId: Long,
    val sourceAssetId: Long,
    val sourcePhotoPath: String,
    val normalizedX: Float,
    val normalizedY: Float,
    val normalizedWidth: Float,
    val normalizedHeight: Float,
    val rotationDegrees: Int,
    val label: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

enum class FormulaResultState {
    QUEUED,
    RUNNING,
    READY,
    FAILED,
    CANCELLED,
}

data class FormulaResult(
    val id: Long,
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
