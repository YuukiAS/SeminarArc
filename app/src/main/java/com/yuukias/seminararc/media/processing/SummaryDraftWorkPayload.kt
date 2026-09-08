package com.yuukias.seminararc.media.processing

import kotlinx.serialization.Serializable

@Serializable
data class SummaryDraftWorkPayload(
    val seminarId: Long,
    val transcriptId: Long,
    val selectedSegmentIds: List<Long>,
    val userNotes: String = "",
) {
    init {
        require(seminarId > 0L) { "Seminar id must be positive." }
        require(transcriptId > 0L) { "Transcript id must be positive." }
        require(selectedSegmentIds.isNotEmpty()) { "Selected segment ids must not be empty." }
        selectedSegmentIds.forEach { segmentId ->
            require(segmentId > 0L) { "Selected segment id must be positive." }
        }
    }
}
