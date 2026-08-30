package com.yuukias.seminararc.domain.model

import java.time.Instant

enum class SeminarAssetType {
    ABSTRACT_PDF,
    RECORDING,
    PHOTO_ORIGINAL,
    PHOTO_ENHANCED,
    AUDIO_CLIP,
    EXPORT,
}

data class SeminarAsset(
    val id: Long,
    val seminarId: Long,
    val type: SeminarAssetType,
    val originAssetId: Long?,
    val sourceTimelineEventId: Long?,
    val sourceRecordingId: Long?,
    val sourceClipId: Long?,
    val relativePath: String?,
    val mimeType: String?,
    val displayName: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

enum class ProcessingJobType {
    IMAGE_ENHANCEMENT,
    TEXT_OCR,
}

enum class ProcessingJobState {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
}

data class ProcessingJob(
    val id: Long,
    val seminarId: Long,
    val type: ProcessingJobType,
    val state: ProcessingJobState,
    val inputAssetId: Long,
    val outputAssetId: Long?,
    val providerId: String,
    val providerVersion: String,
    val createdAt: Instant,
    val startedAt: Instant?,
    val completedAt: Instant?,
    val retryCount: Int,
    val isRetryable: Boolean,
    val errorMessage: String?,
)

data class OcrResult(
    val id: Long,
    val seminarId: Long,
    val assetId: Long,
    val recognizedText: String,
    val editedText: String?,
    val blockJson: String?,
    val languageHint: String?,
    val confidence: Float?,
    val providerId: String,
    val providerVersion: String,
    val isEdited: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
)

enum class SeminarSystemTag {
    KEY_SLIDE,
    BACKGROUND,
    METHOD,
    RESULT,
    REFERENCE,
    FORMULA,
    FOLLOW_UP,
}

data class AssetTag(
    val id: Long,
    val seminarId: Long?,
    val key: String,
    val label: String,
    val isSystem: Boolean,
    val createdAt: Instant,
)
