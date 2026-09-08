package com.yuukias.seminararc.domain.summary

data class SummaryTranscriptWindow(
    val segmentId: Long,
    val startOffsetMs: Long,
    val endOffsetMs: Long,
    val text: String,
) {
    init {
        require(segmentId > 0L) { "Segment id must be positive." }
        require(startOffsetMs >= 0L) { "Window start offset must be non-negative." }
        require(endOffsetMs > startOffsetMs) { "Window end offset must be greater than start offset." }
        require(text.isNotBlank()) { "Window text must not be blank." }
    }
}

data class SummaryRequest(
    val seminarId: Long,
    val title: String,
    val speaker: String?,
    val affiliation: String?,
    val abstractText: String?,
    val transcriptWindows: List<SummaryTranscriptWindow>,
    val referenceTitles: List<String>,
    val keySlideCaptions: List<String>,
    val userNotes: String,
    val inputFingerprint: String,
) {
    init {
        require(seminarId > 0L) { "Seminar id must be positive." }
        require(title.isNotBlank()) { "Seminar title must not be blank." }
        require(inputFingerprint.isNotBlank()) { "Input fingerprint must not be blank." }
    }
}

data class SummaryDraftContent(
    val backgroundContext: String,
    val coreQuestion: String,
    val methods: String,
    val mainResults: String,
    val keyTakeaways: String,
    val unresolvedQuestions: String,
    val followUpActions: String,
    val userNotes: String,
    val provenanceJson: String = "{}",
)

sealed interface SummaryResult {
    data class Drafted(val content: SummaryDraftContent) : SummaryResult
    data class Failed(val message: String, val isRetryable: Boolean = true) : SummaryResult {
        init {
            require(message.isNotBlank()) { "Failure message must not be blank." }
        }
    }
}

interface SummaryProvider {
    val providerId: String
    val providerVersion: String

    suspend fun draft(request: SummaryRequest): SummaryResult
}
