package com.yuukias.seminararc.ui.session

import com.yuukias.seminararc.domain.model.RecordingSession
import com.yuukias.seminararc.domain.model.SeminarDetail
import com.yuukias.seminararc.domain.model.TimelineEvent
import java.time.Instant

enum class CaptureSessionMode {
    RECORD_AND_PHOTOS,
    PHOTOS_ONLY,
}

data class LastPhotoFeedback(
    val event: TimelineEvent,
    val absolutePath: String?,
)

sealed interface ActiveSessionUiState {
    data object Loading : ActiveSessionUiState

    data class Recording(
        val detail: SeminarDetail,
        val recording: RecordingSession?,
        val mode: CaptureSessionMode,
        val elapsedStartedAt: Instant,
        val statusText: String,
        val notificationPermissionGranted: Boolean?,
        val eventCount: Int,
        val lastPhoto: LastPhotoFeedback?,
        val isCapturingPhoto: Boolean,
        val actionMessage: String?,
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
    data object MarkMoment : ActiveSessionAction
    data object CaptureSlide : ActiveSessionAction
    data object UndoLastPhoto : ActiveSessionAction
    data object RetakeLastPhoto : ActiveSessionAction
    data class AddQuestion(val text: String) : ActiveSessionAction
    data class AddNote(val text: String) : ActiveSessionAction
    data class PhotoCaptureCompleted(val relativePath: String) : ActiveSessionAction
    data class PhotoCaptureFailed(val relativePath: String, val message: String) : ActiveSessionAction
    data object EndSeminarConfirmed : ActiveSessionAction
    data object DismissPermissionDenied : ActiveSessionAction
}

sealed interface ActiveSessionEvent {
    data class NavigateToDetail(val seminarId: Long) : ActiveSessionEvent
    data class NavigateToActiveSession(val seminarId: Long) : ActiveSessionEvent
    data class CapturePhoto(val absolutePath: String, val relativePath: String) : ActiveSessionEvent
    data class ShowMessage(val message: String) : ActiveSessionEvent
}
