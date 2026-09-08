package com.yuukias.seminararc.domain.repository

import com.yuukias.seminararc.domain.model.FormulaRegion
import com.yuukias.seminararc.domain.model.FormulaResult
import kotlinx.coroutines.flow.Flow

interface FormulaRepository {
    fun observeRegionsForSeminar(seminarId: Long): Flow<List<FormulaRegion>>

    fun observeResultsForSeminar(seminarId: Long): Flow<List<FormulaResult>>

    suspend fun createRegion(input: CreateFormulaRegionInput): FormulaRegion?

    suspend fun deleteRegion(regionId: Long): Boolean
}

data class CreateFormulaRegionInput(
    val seminarId: Long,
    val sourceAssetId: Long,
    val normalizedX: Float,
    val normalizedY: Float,
    val normalizedWidth: Float,
    val normalizedHeight: Float,
    val rotationDegrees: Int = 0,
    val label: String? = null,
) {
    init {
        require(seminarId > 0L) { "Seminar id must be positive." }
        require(sourceAssetId > 0L) { "Source asset id must be positive." }
        require(normalizedX in 0f..1f) { "Region x must be between 0 and 1." }
        require(normalizedY in 0f..1f) { "Region y must be between 0 and 1." }
        require(normalizedWidth > 0f && normalizedWidth <= 1f) { "Region width must be greater than 0 and at most 1." }
        require(normalizedHeight > 0f && normalizedHeight <= 1f) {
            "Region height must be greater than 0 and at most 1."
        }
        require(normalizedX + normalizedWidth <= 1.0001f) { "Region must fit within the source image width." }
        require(normalizedY + normalizedHeight <= 1.0001f) { "Region must fit within the source image height." }
        require(rotationDegrees in -359..359) { "Rotation degrees must be between -359 and 359." }
    }
}
