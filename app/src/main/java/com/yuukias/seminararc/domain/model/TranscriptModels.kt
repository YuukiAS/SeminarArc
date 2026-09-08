package com.yuukias.seminararc.domain.model

import java.time.Instant

enum class TranscriptState {
    QUEUED,
    RUNNING,
    READY,
    FAILED,
    CANCELLED,
}

enum class TranscriptSourceType {
    RECORDING,
    AUDIO_CLIP,
    MANUAL,
}

enum class TranscriptLanguageHint {
    AUTO,
    ZH,
    EN,
    MIXED,
}

data class Transcript(
    val id: Long,
    val seminarId: Long,
    val recordingId: Long?,
    val providerId: String,
    val providerVersion: String,
    val languageHint: TranscriptLanguageHint,
    val state: TranscriptState,
    val sourceType: TranscriptSourceType,
    val sourceAssetId: Long?,
    val errorMessage: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class TranscriptSegment(
    val id: Long,
    val transcriptId: Long,
    val seminarId: Long,
    val recordingId: Long?,
    val startOffsetMs: Long,
    val endOffsetMs: Long,
    val speakerLabel: String?,
    val language: String?,
    val text: String,
    val confidence: Float?,
    val isEdited: Boolean,
    val providerSegmentId: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

enum class SummaryDraftState {
    DRAFT,
    QUEUED,
    RUNNING,
    READY,
    FAILED,
    CANCELLED,
}

data class SummaryDraft(
    val id: Long,
    val seminarId: Long,
    val providerId: String,
    val inputFingerprint: String,
    val state: SummaryDraftState,
    val backgroundContext: String,
    val coreQuestion: String,
    val methods: String,
    val mainResults: String,
    val keyTakeaways: String,
    val unresolvedQuestions: String,
    val followUpActions: String,
    val userNotes: String,
    val provenanceJson: String,
    val errorMessage: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
