package com.yuukias.seminararc.data.repository

import com.yuukias.seminararc.data.local.dao.FormulaDao
import com.yuukias.seminararc.data.local.dao.ReconstructionDao
import com.yuukias.seminararc.data.local.entity.FormulaRegionEntity
import com.yuukias.seminararc.data.local.entity.FormulaResultEntity
import com.yuukias.seminararc.domain.model.FormulaRegion
import com.yuukias.seminararc.domain.model.FormulaResult
import com.yuukias.seminararc.domain.model.SeminarAssetType
import com.yuukias.seminararc.domain.repository.CreateFormulaRegionInput
import com.yuukias.seminararc.domain.repository.FormulaRepository
import com.yuukias.seminararc.domain.repository.SaveFormulaResultInput
import com.yuukias.seminararc.util.ClockProvider
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class FormulaRepositoryImpl @Inject constructor(
    private val formulaDao: FormulaDao,
    private val reconstructionDao: ReconstructionDao,
    private val clockProvider: ClockProvider,
) : FormulaRepository {
    override fun observeRegionsForSeminar(seminarId: Long): Flow<List<FormulaRegion>> {
        return formulaDao.observeRegionsForSeminar(seminarId).map { regions -> regions.map { it.toDomain() } }
    }

    override fun observeResultsForSeminar(seminarId: Long): Flow<List<FormulaResult>> {
        return formulaDao.observeResultsForSeminar(seminarId).map { results -> results.map { it.toDomain() } }
    }

    override suspend fun getRegion(regionId: Long): FormulaRegion? {
        return formulaDao.getRegion(regionId)?.toDomain()
    }

    override suspend fun createRegion(input: CreateFormulaRegionInput): FormulaRegion? {
        val sourceAsset = reconstructionDao.getAsset(input.sourceAssetId)
            ?.takeIf { asset -> asset.seminarId == input.seminarId }
            ?.takeIf { asset -> asset.type == SeminarAssetType.PHOTO_ORIGINAL || asset.type == SeminarAssetType.PHOTO_ENHANCED }
            ?: return null
        val sourcePath = sourceAsset.relativePath?.takeIf { it.isNotBlank() } ?: return null
        val now = clockProvider.now()
        val id = formulaDao.insertRegion(
            FormulaRegionEntity(
                seminarId = input.seminarId,
                sourceAssetId = input.sourceAssetId,
                sourcePhotoPath = sourcePath,
                normalizedX = input.normalizedX,
                normalizedY = input.normalizedY,
                normalizedWidth = input.normalizedWidth,
                normalizedHeight = input.normalizedHeight,
                rotationDegrees = input.rotationDegrees,
                label = input.label?.trim()?.takeIf { it.isNotBlank() },
                createdAt = now,
                updatedAt = now,
            ),
        )
        return formulaDao.getRegion(id)?.toDomain()
    }

    override suspend fun deleteRegion(regionId: Long): Boolean {
        return formulaDao.deleteRegion(regionId) > 0
    }

    override suspend fun saveFormulaResult(input: SaveFormulaResultInput): FormulaResult? {
        val region = formulaDao.getRegion(input.regionId) ?: return null
        val now = clockProvider.now()
        val existing = formulaDao.getLatestResultForRegionProvider(input.regionId, input.providerId)
        val entity = FormulaResultEntity(
            id = existing?.id ?: 0L,
            seminarId = region.seminarId,
            regionId = input.regionId,
            providerId = input.providerId,
            providerVersion = input.providerVersion,
            state = input.state,
            latex = input.latex,
            confidence = input.confidence,
            isEdited = input.isEdited,
            errorMessage = input.errorMessage,
            provenanceJson = input.provenanceJson,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        if (existing == null) {
            val id = formulaDao.insertResult(entity)
            return formulaDao.getResult(id)?.toDomain()
        }
        formulaDao.updateResult(entity)
        return formulaDao.getResult(entity.id)?.toDomain()
    }

    private fun FormulaRegionEntity.toDomain(): FormulaRegion {
        return FormulaRegion(
            id = id,
            seminarId = seminarId,
            sourceAssetId = sourceAssetId,
            sourcePhotoPath = sourcePhotoPath,
            normalizedX = normalizedX,
            normalizedY = normalizedY,
            normalizedWidth = normalizedWidth,
            normalizedHeight = normalizedHeight,
            rotationDegrees = rotationDegrees,
            label = label,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }

    private fun FormulaResultEntity.toDomain(): FormulaResult {
        return FormulaResult(
            id = id,
            seminarId = seminarId,
            regionId = regionId,
            providerId = providerId,
            providerVersion = providerVersion,
            state = state,
            latex = latex,
            confidence = confidence,
            isEdited = isEdited,
            errorMessage = errorMessage,
            provenanceJson = provenanceJson,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }
}
