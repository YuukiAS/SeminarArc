package com.yuukias.seminararc.domain.usecase

import com.yuukias.seminararc.data.storage.MediaStorageManager
import com.yuukias.seminararc.domain.model.OcrResult
import com.yuukias.seminararc.domain.model.ProcessingJob
import com.yuukias.seminararc.domain.model.ProcessingJobType
import com.yuukias.seminararc.domain.model.SeminarAssetType
import com.yuukias.seminararc.domain.ocr.TextOcrLanguageMode
import com.yuukias.seminararc.domain.ocr.TextOcrProvider
import com.yuukias.seminararc.domain.ocr.TextOcrResult
import com.yuukias.seminararc.domain.repository.EnqueueProcessingJobInput
import com.yuukias.seminararc.domain.repository.ReconstructionRepository
import com.yuukias.seminararc.domain.repository.SaveOcrResultInput
import javax.inject.Inject

class RunTextOcrForAssetUseCase @Inject constructor(
    private val reconstructionRepository: ReconstructionRepository,
    private val mediaStorageManager: MediaStorageManager,
    private val textOcrProvider: TextOcrProvider,
) {
    suspend operator fun invoke(
        assetId: Long,
        languageMode: TextOcrLanguageMode = TextOcrLanguageMode.LATIN_AND_CHINESE,
    ): RunTextOcrResult {
        val asset = reconstructionRepository.getAsset(assetId)
            ?: return RunTextOcrResult.Failed("Source asset was not found.")
        if (asset.type != SeminarAssetType.PHOTO_ORIGINAL && asset.type != SeminarAssetType.PHOTO_ENHANCED) {
            return RunTextOcrResult.Failed("Only photo assets can be OCR processed.")
        }
        val relativePath = asset.relativePath
            ?: return RunTextOcrResult.Failed("Source asset has no local file path.")
        val source = mediaStorageManager.resolveReadableRelativeFile(relativePath)
            ?: return RunTextOcrResult.Failed("Source image file is not readable.")
        val job = reconstructionRepository.enqueueJob(
            EnqueueProcessingJobInput(
                seminarId = asset.seminarId,
                type = ProcessingJobType.TEXT_OCR,
                inputAssetId = asset.id,
                providerId = textOcrProvider.providerId,
                providerVersion = textOcrProvider.providerVersion,
            ),
        )
        reconstructionRepository.markJobRunning(job.id)
        return when (val ocr = textOcrProvider.recognize(source, languageMode)) {
            is TextOcrResult.Recognized -> {
                val result = reconstructionRepository.saveOcrResult(
                    SaveOcrResultInput(
                        seminarId = asset.seminarId,
                        assetId = asset.id,
                        recognizedText = ocr.recognition.recognizedText,
                        editedText = null,
                        blockJson = ocr.recognition.blockJson,
                        languageHint = ocr.recognition.languageHint,
                        confidence = ocr.recognition.confidence,
                        providerId = textOcrProvider.providerId,
                        providerVersion = textOcrProvider.providerVersion,
                    ),
                )
                reconstructionRepository.markJobSucceeded(job.id, outputAssetId = asset.id)
                RunTextOcrResult.Recognized(result, job)
            }
            is TextOcrResult.Failed -> {
                reconstructionRepository.markJobFailed(job.id, ocr.message, isRetryable = ocr.isRetryable)
                RunTextOcrResult.Failed(ocr.message)
            }
        }
    }
}

sealed interface RunTextOcrResult {
    data class Recognized(
        val ocrResult: OcrResult,
        val job: ProcessingJob,
    ) : RunTextOcrResult

    data class Failed(val message: String) : RunTextOcrResult
}
