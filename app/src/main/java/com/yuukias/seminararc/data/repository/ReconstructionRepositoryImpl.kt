package com.yuukias.seminararc.data.repository

import com.yuukias.seminararc.data.local.dao.ReconstructionDao
import com.yuukias.seminararc.data.local.entity.AssetTagEntity
import com.yuukias.seminararc.data.local.entity.OcrResultEntity
import com.yuukias.seminararc.data.local.entity.ProcessingJobEntity
import com.yuukias.seminararc.data.local.entity.SeminarAssetEntity
import com.yuukias.seminararc.data.local.entity.TagEntity
import com.yuukias.seminararc.domain.model.AssetTag
import com.yuukias.seminararc.domain.model.OcrResult
import com.yuukias.seminararc.domain.model.ProcessingJob
import com.yuukias.seminararc.domain.model.ProcessingJobState
import com.yuukias.seminararc.domain.model.ProcessingJobType
import com.yuukias.seminararc.domain.model.SeminarAsset
import com.yuukias.seminararc.domain.model.SeminarAssetType
import com.yuukias.seminararc.domain.model.SeminarSystemTag
import com.yuukias.seminararc.domain.repository.CreateDerivedAssetInput
import com.yuukias.seminararc.domain.repository.EnqueueProcessingJobInput
import com.yuukias.seminararc.domain.repository.ReconstructionRepository
import com.yuukias.seminararc.domain.repository.SaveOcrResultInput
import com.yuukias.seminararc.util.ClockProvider
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ReconstructionRepositoryImpl @Inject constructor(
    private val dao: ReconstructionDao,
    private val clockProvider: ClockProvider,
) : ReconstructionRepository {

    override fun observeAssetsForSeminar(seminarId: Long): Flow<List<SeminarAsset>> {
        return dao.observeAssetsForSeminar(seminarId).map { assets -> assets.map { it.toDomain() } }
    }

    override fun observePhotoAssetsForSeminar(seminarId: Long): Flow<List<SeminarAsset>> {
        return dao.observeAssetsForSeminarByTypes(
            seminarId = seminarId,
            types = listOf(SeminarAssetType.PHOTO_ORIGINAL, SeminarAssetType.PHOTO_ENHANCED),
        ).map { assets -> assets.map { it.toDomain() } }
    }

    override fun observeJobsForSeminar(seminarId: Long): Flow<List<ProcessingJob>> {
        return dao.observeJobsForSeminar(seminarId).map { jobs -> jobs.map { it.toDomain() } }
    }

    override fun observeOcrResultsForSeminar(seminarId: Long): Flow<List<OcrResult>> {
        return dao.observeOcrResultsForSeminar(seminarId).map { results -> results.map { it.toDomain() } }
    }

    override fun observeTagsForAsset(assetId: Long): Flow<List<AssetTag>> {
        return dao.observeTagsForAsset(assetId).map { tags -> tags.map { it.toDomain() } }
    }

    override suspend fun getAsset(assetId: Long): SeminarAsset? {
        return dao.getAsset(assetId)?.toDomain()
    }

    override suspend fun createDerivedAsset(input: CreateDerivedAssetInput): SeminarAsset {
        require(input.type == SeminarAssetType.PHOTO_ENHANCED) {
            "Only PHOTO_ENHANCED derived assets are supported in 0.2.x."
        }
        require(input.relativePath.isNotBlank()) { "Derived asset path must not be blank." }
        val now = clockProvider.now()
        val id = dao.insertAsset(
            SeminarAssetEntity(
                seminarId = input.seminarId,
                type = input.type,
                originAssetId = input.originAssetId,
                sourceTimelineEventId = null,
                sourceRecordingId = null,
                sourceClipId = null,
                relativePath = input.relativePath,
                mimeType = input.mimeType,
                displayName = input.displayName,
                createdAt = now,
                updatedAt = now,
            ),
        )
        return dao.getAsset(id)?.toDomain() ?: error("Asset $id was not readable after insert.")
    }

    override suspend fun enqueueJob(input: EnqueueProcessingJobInput): ProcessingJob {
        dao.getActiveJobForInput(input.inputAssetId, input.type)?.let { existing ->
            return existing.toDomain()
        }
        val now = clockProvider.now()
        val id = dao.insertJob(
            ProcessingJobEntity(
                seminarId = input.seminarId,
                type = input.type,
                state = ProcessingJobState.QUEUED,
                inputAssetId = input.inputAssetId,
                outputAssetId = null,
                providerId = input.providerId,
                providerVersion = input.providerVersion,
                createdAt = now,
                startedAt = null,
                completedAt = null,
                retryCount = 0,
                isRetryable = true,
                errorMessage = null,
            ),
        )
        return dao.getJob(id)?.toDomain() ?: error("Job $id was not readable after insert.")
    }

    override suspend fun markJobRunning(jobId: Long) {
        val existing = dao.getJob(jobId) ?: return
        dao.updateJob(
            existing.copy(
                state = ProcessingJobState.RUNNING,
                startedAt = existing.startedAt ?: clockProvider.now(),
                completedAt = null,
                errorMessage = null,
            ),
        )
    }

    override suspend fun markJobSucceeded(jobId: Long, outputAssetId: Long?) {
        val existing = dao.getJob(jobId) ?: return
        dao.updateJob(
            existing.copy(
                state = ProcessingJobState.SUCCEEDED,
                outputAssetId = outputAssetId,
                completedAt = clockProvider.now(),
                isRetryable = false,
                errorMessage = null,
            ),
        )
    }

    override suspend fun markJobFailed(jobId: Long, message: String, isRetryable: Boolean) {
        val existing = dao.getJob(jobId) ?: return
        dao.updateJob(
            existing.copy(
                state = ProcessingJobState.FAILED,
                completedAt = clockProvider.now(),
                retryCount = existing.retryCount + 1,
                isRetryable = isRetryable,
                errorMessage = message.take(MAX_ERROR_LENGTH),
            ),
        )
    }

    override suspend fun markJobCancelled(jobId: Long) {
        val existing = dao.getJob(jobId) ?: return
        dao.updateJob(
            existing.copy(
                state = ProcessingJobState.CANCELLED,
                completedAt = clockProvider.now(),
                isRetryable = true,
            ),
        )
    }

    override suspend fun saveOcrResult(input: SaveOcrResultInput): OcrResult {
        val now = clockProvider.now()
        val existing = dao.getOcrResultForAsset(input.assetId)
        val entity = OcrResultEntity(
            id = existing?.id ?: 0L,
            seminarId = input.seminarId,
            assetId = input.assetId,
            recognizedText = input.recognizedText,
            editedText = input.editedText?.takeIf { it.isNotBlank() },
            blockJson = input.blockJson,
            languageHint = input.languageHint,
            confidence = input.confidence,
            providerId = input.providerId,
            providerVersion = input.providerVersion,
            isEdited = input.editedText != null,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        val id = dao.upsertOcrResult(entity)
        return (dao.getOcrResultForAsset(input.assetId) ?: entity.copy(id = id)).toDomain()
    }

    override suspend fun setSystemTag(assetId: Long, tag: SeminarSystemTag, enabled: Boolean) {
        val now = clockProvider.now()
        val tagEntity = dao.ensureSystemTag(
            TagEntity(
                key = tag.name,
                label = tag.toLabel(),
                seminarId = null,
                isSystem = true,
                createdAt = now,
            ),
        )
        if (enabled) {
            dao.insertAssetTag(AssetTagEntity(assetId = assetId, tagId = tagEntity.id))
        } else {
            dao.deleteAssetTag(assetId, tagEntity.id)
        }
    }

    private fun SeminarAssetEntity.toDomain(): SeminarAsset {
        return SeminarAsset(
            id = id,
            seminarId = seminarId,
            type = type,
            originAssetId = originAssetId,
            sourceTimelineEventId = sourceTimelineEventId,
            sourceRecordingId = sourceRecordingId,
            sourceClipId = sourceClipId,
            relativePath = relativePath,
            mimeType = mimeType,
            displayName = displayName,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }

    private fun ProcessingJobEntity.toDomain(): ProcessingJob {
        return ProcessingJob(
            id = id,
            seminarId = seminarId,
            type = type,
            state = state,
            inputAssetId = inputAssetId,
            outputAssetId = outputAssetId,
            providerId = providerId,
            providerVersion = providerVersion,
            createdAt = createdAt,
            startedAt = startedAt,
            completedAt = completedAt,
            retryCount = retryCount,
            isRetryable = isRetryable,
            errorMessage = errorMessage,
        )
    }

    private fun OcrResultEntity.toDomain(): OcrResult {
        return OcrResult(
            id = id,
            seminarId = seminarId,
            assetId = assetId,
            recognizedText = recognizedText,
            editedText = editedText,
            blockJson = blockJson,
            languageHint = languageHint,
            confidence = confidence,
            providerId = providerId,
            providerVersion = providerVersion,
            isEdited = isEdited,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }

    private fun TagEntity.toDomain(): AssetTag {
        return AssetTag(
            id = id,
            seminarId = seminarId,
            key = key,
            label = label,
            isSystem = isSystem,
            createdAt = createdAt,
        )
    }

    private fun SeminarSystemTag.toLabel(): String {
        return when (this) {
            SeminarSystemTag.KEY_SLIDE -> "Key slide"
            SeminarSystemTag.BACKGROUND -> "Background"
            SeminarSystemTag.METHOD -> "Method"
            SeminarSystemTag.RESULT -> "Result"
            SeminarSystemTag.REFERENCE -> "Reference"
            SeminarSystemTag.FORMULA -> "Formula"
            SeminarSystemTag.FOLLOW_UP -> "Follow up"
        }
    }

    private companion object {
        const val MAX_ERROR_LENGTH = 1_000
    }
}
