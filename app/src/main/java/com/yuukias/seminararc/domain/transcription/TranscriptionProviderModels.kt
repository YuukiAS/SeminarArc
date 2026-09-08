package com.yuukias.seminararc.domain.transcription

import com.yuukias.seminararc.domain.model.TranscriptLanguageHint
import java.io.File

enum class TranscriptTimestampGranularity {
    SEGMENT,
    WORD,
}

data class TranscriptionRequest(
    val seminarId: Long,
    val recordingId: Long,
    val sourceAudio: File,
    val languageHint: TranscriptLanguageHint = TranscriptLanguageHint.AUTO,
    val requestedGranularity: TranscriptTimestampGranularity = TranscriptTimestampGranularity.SEGMENT,
    val sourceAssetId: Long? = null,
) {
    init {
        require(seminarId > 0L) { "Seminar id must be positive." }
        require(recordingId > 0L) { "Recording id must be positive." }
        sourceAssetId?.let { require(it > 0L) { "Source asset id must be positive." } }
    }
}

data class TranscriptionSegmentDraft(
    val startOffsetMs: Long,
    val endOffsetMs: Long,
    val text: String,
    val speakerLabel: String? = null,
    val language: String? = null,
    val confidence: Float? = null,
    val providerSegmentId: String? = null,
) {
    init {
        require(startOffsetMs >= 0L) { "Segment start offset must be non-negative." }
        require(endOffsetMs > startOffsetMs) { "Segment end offset must be greater than start offset." }
        require(text.isNotBlank()) { "Segment text must not be blank." }
        confidence?.let { require(it in 0f..1f) { "Segment confidence must be between 0 and 1." } }
    }
}

data class TranscriptionRecognition(
    val languageHint: TranscriptLanguageHint,
    val segments: List<TranscriptionSegmentDraft>,
    val provenanceJson: String = "{}",
) {
    init {
        require(segments.isNotEmpty()) { "Transcript must contain at least one segment." }
        segments.zipWithNext().forEach { (previous, next) ->
            require(next.startOffsetMs >= previous.startOffsetMs) {
                "Transcript segments must be sorted by start offset."
            }
        }
    }
}

sealed interface TranscriptionResult {
    data class Transcribed(val recognition: TranscriptionRecognition) : TranscriptionResult
    data class Failed(val message: String, val isRetryable: Boolean = true) : TranscriptionResult {
        init {
            require(message.isNotBlank()) { "Failure message must not be blank." }
        }
    }
}

interface TranscriptionProvider {
    val providerId: String
    val providerVersion: String

    suspend fun transcribe(request: TranscriptionRequest): TranscriptionResult
}
