package com.yuukias.seminararc.ui.session

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.yuukias.seminararc.domain.model.ActiveSeminarSessionState
import com.yuukias.seminararc.domain.model.EndSeminarResult
import com.yuukias.seminararc.domain.model.RecordingServiceState
import com.yuukias.seminararc.domain.model.RecordingState
import com.yuukias.seminararc.domain.model.SeminarDetail
import com.yuukias.seminararc.domain.model.SeminarSessionRecoveryReason
import com.yuukias.seminararc.domain.model.SeminarStatus
import com.yuukias.seminararc.domain.model.StartSeminarRecordingResult
import com.yuukias.seminararc.domain.repository.RecordingRepository
import com.yuukias.seminararc.domain.repository.SeminarRepository
import com.yuukias.seminararc.domain.usecase.EndSeminarUseCase
import com.yuukias.seminararc.domain.usecase.StartSeminarRecordingUseCase
import com.yuukias.seminararc.recording.service.RecordingPermissionChecker
import com.yuukias.seminararc.recording.service.RecordingRuntimeStateProvider
import com.yuukias.seminararc.ui.navigation.ActiveSessionRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class ActiveSessionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val seminarRepository: SeminarRepository,
    private val recordingRepository: RecordingRepository,
    private val runtimeStateProvider: RecordingRuntimeStateProvider,
    private val startSeminarRecordingUseCase: StartSeminarRecordingUseCase,
    private val endSeminarUseCase: EndSeminarUseCase,
    private val permissionChecker: RecordingPermissionChecker,
) : ViewModel() {

    private val seminarId: Long = savedStateHandle["seminarId"]
        ?: savedStateHandle.toRoute<ActiveSessionRoute>().seminarId
    private val permissionDenied = MutableStateFlow(false)
    private val ending = MutableStateFlow(false)

    val uiState: StateFlow<ActiveSessionUiState> = combine(
        seminarRepository.observeSeminarDetail(seminarId),
        recordingRepository.observeLatestRecordingForSeminar(seminarId),
        runtimeStateProvider.state,
        permissionDenied,
        ending,
    ) { detail, recording, runtimeState, isPermissionDenied, isEnding ->
        ActiveSessionSnapshot(
            detail = detail,
            recording = recording,
            runtimeState = runtimeState,
            isPermissionDenied = isPermissionDenied,
            isEnding = isEnding,
        )
    }
        .mapLatest { snapshot -> snapshot.toUiState(seminarRepository.getActiveSeminarSessionState()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActiveSessionUiState.Loading)

    private val _events = MutableSharedFlow<ActiveSessionEvent>(replay = 0)
    val events: SharedFlow<ActiveSessionEvent> = _events.asSharedFlow()

    fun onAction(action: ActiveSessionAction) {
        when (action) {
            ActiveSessionAction.ResumeRecording -> resumeRecording()
            ActiveSessionAction.EndSeminarConfirmed -> endSeminar()
            ActiveSessionAction.DismissPermissionDenied -> permissionDenied.value = false
        }
    }

    private fun resumeRecording() {
        viewModelScope.launch {
            if (!permissionChecker.hasRecordAudioPermission()) {
                permissionDenied.value = true
                return@launch
            }
            when (val result = startSeminarRecordingUseCase(seminarId)) {
                is StartSeminarRecordingResult.Started -> {
                    permissionDenied.value = false
                    if (!result.notificationPermissionGranted) {
                        _events.emit(
                            ActiveSessionEvent.ShowMessage(
                                "Recording started. Notifications are blocked, so Android may only show the foreground service in system task controls.",
                            ),
                        )
                    }
                }
                is StartSeminarRecordingResult.AudioPermissionDenied -> permissionDenied.value = true
                is StartSeminarRecordingResult.AnotherSeminarActive -> {
                    _events.emit(ActiveSessionEvent.NavigateToActiveSession(result.activeSession.seminarId))
                }
                is StartSeminarRecordingResult.RecoveryRequired -> {
                    _events.emit(ActiveSessionEvent.ShowMessage("Recovery is required before a new recording can start: ${result.reason}."))
                }
                is StartSeminarRecordingResult.CannotStart -> {
                    _events.emit(ActiveSessionEvent.ShowMessage(result.reason))
                }
            }
        }
    }

    private fun endSeminar() {
        viewModelScope.launch {
            ending.value = true
            when (val result = endSeminarUseCase(seminarId)) {
                is EndSeminarResult.Completed,
                is EndSeminarResult.AlreadyCompleted -> {
                    ending.value = false
                    _events.emit(ActiveSessionEvent.NavigateToDetail(seminarId))
                }
                is EndSeminarResult.StopFailed -> {
                    ending.value = false
                    _events.emit(ActiveSessionEvent.ShowMessage(result.message))
                }
                is EndSeminarResult.CannotComplete -> {
                    ending.value = false
                    _events.emit(ActiveSessionEvent.ShowMessage(result.reason))
                }
                is EndSeminarResult.NotFound -> {
                    ending.value = false
                    _events.emit(ActiveSessionEvent.ShowMessage("Seminar was not found."))
                }
            }
        }
    }

    private fun ActiveSessionSnapshot.toUiState(
        activeState: ActiveSeminarSessionState,
    ): ActiveSessionUiState {
        if (isEnding) {
            return ActiveSessionUiState.Ending(detail)
        }
        if (isPermissionDenied) {
            return ActiveSessionUiState.PermissionDenied(seminarId)
        }

        val currentDetail = detail ?: return ActiveSessionUiState.Failed(
            title = "Seminar unavailable",
            message = "This seminar could not be loaded.",
            canRetry = false,
        )

        if (currentDetail.status == SeminarStatus.COMPLETED) {
            return ActiveSessionUiState.Completed(currentDetail.id)
        }

        when (activeState) {
            ActiveSeminarSessionState.None -> {
                return ActiveSessionUiState.RecoveryRequired(
                    detail = currentDetail,
                    title = "No active seminar session",
                    message = "This route was opened without an active seminar. Return to detail and start the seminar again.",
                    canResume = currentDetail.status == SeminarStatus.DRAFT,
                    canEnd = false,
                )
            }
            is ActiveSeminarSessionState.Active -> {
                if (activeState.session.seminarId != seminarId) {
                    return ActiveSessionUiState.RecoveryRequired(
                        detail = currentDetail,
                        title = "Another seminar is active",
                        message = "Seminar \"${activeState.session.title}\" is already active. SeminarArc will not create a second live session.",
                        canResume = false,
                        canEnd = false,
                        conflictSeminarIds = listOf(activeState.session.seminarId),
                    )
                }
            }
            is ActiveSeminarSessionState.RecoveryRequired -> {
                return ActiveSessionUiState.RecoveryRequired(
                    detail = currentDetail,
                    title = activeState.reason.toRecoveryTitle(),
                    message = activeState.reason.toRecoveryMessage(),
                    canResume = false,
                    canEnd = activeState.reason != SeminarSessionRecoveryReason.MULTIPLE_ACTIVE_SEMINARS,
                    conflictSeminarIds = activeState.activeSessions.map { session -> session.seminarId },
                )
            }
        }

        if (currentDetail.sessionStartedAt == null) {
            return ActiveSessionUiState.RecoveryRequired(
                detail = currentDetail,
                title = "Session start time is missing",
                message = "This seminar is active but has no durable start time, so elapsed recording time cannot be trusted.",
                canResume = false,
                canEnd = true,
            )
        }

        if (runtimeState is RecordingServiceState.Recording && runtimeState.recording.seminarId == seminarId) {
            return ActiveSessionUiState.Recording(
                detail = currentDetail,
                recording = runtimeState.recording,
                elapsedStartedAt = runtimeState.recording.startedAt,
                statusText = "Recording",
                notificationPermissionGranted = null,
            )
        }

        if (runtimeState is RecordingServiceState.Starting && runtimeState.seminarId == seminarId) {
            return ActiveSessionUiState.Recording(
                detail = currentDetail,
                recording = recording,
                elapsedStartedAt = recording?.startedAt ?: currentDetail.sessionStartedAt,
                statusText = "Starting recorder",
                notificationPermissionGranted = null,
            )
        }

        if (recording?.state == RecordingState.RECORDING) {
            return ActiveSessionUiState.RecoveryRequired(
                detail = currentDetail,
                title = "Recording recovery required",
                message = "A recording row still says RECORDING, but this process has no live microphone recorder. The old .m4a segment cannot be continued; resuming creates a new segment.",
                canResume = false,
                canEnd = true,
            )
        }

        if (currentDetail.status == SeminarStatus.ACTIVE) {
            return ActiveSessionUiState.RecoveryRequired(
                detail = currentDetail,
                title = "Recording interrupted",
                message = "The previous recording stopped unexpectedly. Existing files and records are preserved, and there is no live recording right now.",
                canResume = true,
                canEnd = true,
            )
        }

        return ActiveSessionUiState.Failed(
            title = "Cannot open active session",
            message = "Only active seminars can use the live capture route.",
            canRetry = false,
        )
    }

    private data class ActiveSessionSnapshot(
        val detail: SeminarDetail?,
        val recording: com.yuukias.seminararc.domain.model.RecordingSession?,
        val runtimeState: RecordingServiceState,
        val isPermissionDenied: Boolean,
        val isEnding: Boolean,
    )
}

private fun SeminarSessionRecoveryReason.toRecoveryTitle(): String {
    return when (this) {
        SeminarSessionRecoveryReason.ACTIVE_WITHOUT_START_TIME -> "Session start time is missing"
        SeminarSessionRecoveryReason.ACTIVE_WITH_END_TIME -> "Active session has an end time"
        SeminarSessionRecoveryReason.MULTIPLE_ACTIVE_SEMINARS -> "Multiple active seminars"
        SeminarSessionRecoveryReason.LOST_UPDATE -> "Active session changed"
    }
}

private fun SeminarSessionRecoveryReason.toRecoveryMessage(): String {
    return when (this) {
        SeminarSessionRecoveryReason.ACTIVE_WITHOUT_START_TIME -> "A seminar is marked ACTIVE without a durable session start time. Manual review is needed before recording resumes."
        SeminarSessionRecoveryReason.ACTIVE_WITH_END_TIME -> "A seminar is marked ACTIVE but already has a session end time. Manual review is needed before recording resumes."
        SeminarSessionRecoveryReason.MULTIPLE_ACTIVE_SEMINARS -> "More than one seminar is marked ACTIVE. SeminarArc will not guess which session owns new recordings."
        SeminarSessionRecoveryReason.LOST_UPDATE -> "The active seminar changed while the operation was being saved. Please reopen the seminar and try again."
    }
}
