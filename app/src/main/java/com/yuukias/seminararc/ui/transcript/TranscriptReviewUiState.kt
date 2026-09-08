package com.yuukias.seminararc.ui.transcript

import com.yuukias.seminararc.domain.model.SeminarDetail
import com.yuukias.seminararc.domain.model.ProcessingJob
import com.yuukias.seminararc.domain.model.SummaryDraft
import com.yuukias.seminararc.domain.model.Transcript
import com.yuukias.seminararc.domain.model.TranscriptSegment
import com.yuukias.seminararc.domain.usecase.TranscriptTimelineWindow

sealed interface TranscriptReviewUiState {
    data object Loading : TranscriptReviewUiState
    data class Missing(val seminarId: Long) : TranscriptReviewUiState
    data class Ready(
        val detail: SeminarDetail,
        val transcripts: List<Transcript>,
        val selectedTranscript: Transcript?,
        val segments: List<TranscriptSegment>,
        val segmentDrafts: Map<Long, String>,
        val manualTranscriptDraft: String,
        val timelineWindows: List<TranscriptTimelineWindow>,
        val summaryDrafts: List<SummaryDraft>,
        val processingJobs: List<ProcessingJob>,
    ) : TranscriptReviewUiState
}

sealed interface TranscriptReviewEvent {
    data class ShowMessage(val message: String) : TranscriptReviewEvent
}
