package com.yuukias.seminararc.ui.session

import com.yuukias.seminararc.domain.model.RecordingSession
import com.yuukias.seminararc.domain.model.SeminarDetail
import java.time.Instant

sealed interface ActiveSessionUiState {
    data object Loading : ActiveSessionUiState

    data class Recording(
        val detail: SeminarDetail,
        val recording: RecordingSession?,
        val elapsedStartedAt: Instant,
        val statusText: String,
        val notificationPermissionGranted: Boolean?,
    ) : ActiveSessionUiState

    data class Ending(
        val detail: SeminarDetail?,
    ) : ActiveSessionUiState

    data class PermissionDenied(
        val seminarId: Long,
    ) : ActiveSessionUiState

    data class RecoveryRequired(
        val detail: SeminarDetail?,
        val title: String,
        val message: String,
        val canResume: Boolean,
        val canEnd: Boolean,
        val conflictSeminarIds: List<Long> = emptyList(),
    ) : ActiveSessionUiState

    data class Failed(
        val title: String,
        val message: String,
        val canRetry: Boolean,
    ) : ActiveSessionUiState

    data class Completed(
        val seminarId: Long,
    ) : ActiveSessionUiState
}

sealed interface ActiveSessionAction {
    data object ResumeRecording : ActiveSessionAction
    data object EndSeminarConfirmed : ActiveSessionAction
    data object DismissPermissionDenied : ActiveSessionAction
}

sealed interface ActiveSessionEvent {
    data class NavigateToDetail(val seminarId: Long) : ActiveSessionEvent
    data class NavigateToActiveSession(val seminarId: Long) : ActiveSessionEvent
    data class ShowMessage(val message: String) : ActiveSessionEvent
}
