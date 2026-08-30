package com.yuukias.seminararc.domain.usecase

import com.yuukias.seminararc.data.storage.MediaStorageManager
import com.yuukias.seminararc.domain.image.ImageEnhancementOptions
import com.yuukias.seminararc.domain.image.ImageEnhancementProvider
import com.yuukias.seminararc.domain.image.ImageEnhancementResult
import com.yuukias.seminararc.domain.model.ProcessingJob
import com.yuukias.seminararc.domain.model.ProcessingJobType
import com.yuukias.seminararc.domain.model.SeminarAsset
import com.yuukias.seminararc.domain.model.SeminarAssetType
import com.yuukias.seminararc.domain.repository.CreateDerivedAssetInput
import com.yuukias.seminararc.domain.repository.EnqueueProcessingJobInput
import com.yuukias.seminararc.domain.repository.ReconstructionRepository
import javax.inject.Inject

class EnhancePhotoAssetUseCase @Inject constructor(
    private val reconstructionRepository: ReconstructionRepository,
    private val mediaStorageManager: MediaStorageManager,
    private val imageEnhancementProvider: ImageEnhancementProvider,
) {
    suspend operator fun invoke(
        originAssetId: Long,
        options: ImageEnhancementOptions = ImageEnhancementOptions(),
    ): EnhancePhotoAssetResult {
        val origin = reconstructionRepository.getAsset(originAssetId)
            ?: return EnhancePhotoAssetResult.Failed("Source asset was not found.")
        if (origin.type != SeminarAssetType.PHOTO_ORIGINAL && origin.type != SeminarAssetType.PHOTO_ENHANCED) {
            return EnhancePhotoAssetResult.Failed("Only photo assets can be enhanced.")
        }
        val sourcePath = origin.relativePath
            ?: return EnhancePhotoAssetResult.Failed("Source asset has no local file path.")
        val source = mediaStorageManager.resolveReadableRelativeFile(sourcePath)
            ?: return EnhancePhotoAssetResult.Failed("Source photo file is not readable.")

        val output = mediaStorageManager.createEnhancedPhotoOutputFile(
            seminarId = origin.seminarId,
            originAssetId = origin.id,
            variantKey = options.variantKey(),
        )
        reconstructionRepository.getAssetByRelativePath(output.relativePath)?.let { existing ->
            return EnhancePhotoAssetResult.AlreadyEnhanced(existing)
        }

        val job = reconstructionRepository.enqueueJob(
            EnqueueProcessingJobInput(
                seminarId = origin.seminarId,
                type = ProcessingJobType.IMAGE_ENHANCEMENT,
                inputAssetId = origin.id,
                providerId = imageEnhancementProvider.providerId,
                providerVersion = imageEnhancementProvider.providerVersion,
            ),
        )
        reconstructionRepository.markJobRunning(job.id)
        return when (val result = imageEnhancementProvider.enhance(source, output.file, options)) {
            is ImageEnhancementResult.Enhanced -> {
                val asset = reconstructionRepository.createDerivedAsset(
                    CreateDerivedAssetInput(
                        seminarId = origin.seminarId,
                        type = SeminarAssetType.PHOTO_ENHANCED,
                        originAssetId = origin.id,
                        relativePath = output.relativePath,
                        mimeType = result.mimeType,
                        displayName = output.displayName,
                    ),
                )
                reconstructionRepository.markJobSucceeded(job.id, asset.id)
                EnhancePhotoAssetResult.Enhanced(asset, job)
            }
            is ImageEnhancementResult.Failed -> {
                reconstructionRepository.markJobFailed(job.id, result.message, isRetryable = true)
                mediaStorageManager.deleteRelativeFile(output.relativePath)
                EnhancePhotoAssetResult.Failed(result.message)
            }
        }
    }
}

sealed interface EnhancePhotoAssetResult {
    data class Enhanced(
        val asset: SeminarAsset,
        val job: ProcessingJob,
    ) : EnhancePhotoAssetResult

    data class AlreadyEnhanced(val asset: SeminarAsset) : EnhancePhotoAssetResult

    data class Failed(val message: String) : EnhancePhotoAssetResult
}
