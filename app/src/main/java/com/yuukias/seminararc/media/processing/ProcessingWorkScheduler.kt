package com.yuukias.seminararc.media.processing

import com.yuukias.seminararc.domain.image.ImageEnhancementOptions
import com.yuukias.seminararc.domain.model.ProcessingJob
import com.yuukias.seminararc.domain.ocr.TextOcrLanguageMode

interface ProcessingWorkScheduler {
    suspend fun enqueueImageEnhancement(
        assetId: Long,
        options: ImageEnhancementOptions = ImageEnhancementOptions(),
    ): ProcessingJob?

    suspend fun enqueueTextOcr(
        assetId: Long,
        languageMode: TextOcrLanguageMode = TextOcrLanguageMode.LATIN_AND_CHINESE,
    ): ProcessingJob?

    suspend fun retry(jobId: Long): ProcessingJob?

    suspend fun cancel(jobId: Long)

    fun recoverProcessingJobs()
}
