package com.yuukias.seminararc.media.processing

import com.yuukias.seminararc.domain.image.ImageEnhancementOptions
import com.yuukias.seminararc.domain.model.ProcessingJob
import com.yuukias.seminararc.domain.model.TranscriptLanguageHint
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

    suspend fun enqueueTranscription(
        recordingId: Long,
        languageHint: TranscriptLanguageHint = TranscriptLanguageHint.AUTO,
    ): ProcessingJob?

    suspend fun enqueueSummaryDraft(
        seminarId: Long,
        transcriptId: Long,
        selectedSegmentIds: List<Long>,
        userNotes: String = "",
    ): ProcessingJob?

    suspend fun enqueueManualFormulaOcr(
        regionId: Long,
        latex: String,
    ): ProcessingJob?

    suspend fun retry(jobId: Long): ProcessingJob?

    suspend fun cancel(jobId: Long)

    fun recoverProcessingJobs()
}
