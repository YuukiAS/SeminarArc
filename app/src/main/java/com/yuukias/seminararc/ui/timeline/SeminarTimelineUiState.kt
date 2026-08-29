package com.yuukias.seminararc.ui.timeline

import com.yuukias.seminararc.domain.model.SeminarDetail
import com.yuukias.seminararc.domain.model.TimelineEvent

sealed interface SeminarTimelineUiState {
    data object Loading : SeminarTimelineUiState

    data class Ready(
        val detail: SeminarDetail,
        val items: List<TimelineEventUiItem>,
        val canPlayFromTimeline: Boolean,
        val playbackLabel: String,
    ) : SeminarTimelineUiState

    data class Missing(val seminarId: Long) : SeminarTimelineUiState
}

data class TimelineEventUiItem(
    val event: TimelineEvent,
    val absolutePhotoPath: String?,
    val photoMissing: Boolean,
)

sealed interface SeminarTimelineEvent {
    data class ShowMessage(val message: String) : SeminarTimelineEvent
}
