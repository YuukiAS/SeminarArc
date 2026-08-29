package com.yuukias.seminararc.ui.session

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.yuukias.seminararc.data.storage.MediaStorageManager
import com.yuukias.seminararc.domain.model.ActiveSeminarSessionState
import com.yuukias.seminararc.domain.model.EndSeminarResult
import com.yuukias.seminararc.domain.model.RecordingServiceState
import com.yuukias.seminararc.domain.model.RecordingState
import com.yuukias.seminararc.domain.model.SeminarDetail
import com.yuukias.seminararc.domain.model.SeminarSessionRecoveryReason
import com.yuukias.seminararc.domain.model.SeminarStatus
import com.yuukias.seminararc.domain.model.StartSeminarRecordingResult
import com.yuukias.seminararc.domain.model.TimelineEvent
import com.yuukias.seminararc.domain.model.TimelineEventType
import com.yuukias.seminararc.domain.repository.RecordingRepository
import com.yuukias.seminararc.domain.repository.SeminarRepository
import com.yuukias.seminararc.domain.repository.ClipRepository
import com.yuukias.seminararc.domain.repository.TimelineRepository
import com.yuukias.seminararc.domain.usecase.CaptureOffsetAnchor
import com.yuukias.seminararc.domain.usecase.CaptureOffsetCalculator
import com.yuukias.seminararc.domain.usecase.EndSeminarUseCase
import com.yuukias.seminararc.domain.usecase.StartSeminarRecordingUseCase
import com.yuukias.seminararc.recording.service.RecordingPermissionChecker
import com.yuukias.seminararc.recording.service.RecordingRuntimeStateProvider
import com.yuukias.seminararc.media.clip.ClipWorkScheduler
import com.yuukias.seminararc.ui.navigation.ActiveSessionRoute
import com.yuukias.seminararc.util.ClockProvider
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
    private val timelineRepository: TimelineRepository,
    private val clipRepository: ClipRepository,
    private val clipWorkScheduler: ClipWorkScheduler,
    private val mediaStorageManager: MediaStorageManager,
    private val runtimeStateProvider: RecordingRuntimeStateProvider,
    private val startSeminarRecordingUseCase: StartSeminarRecordingUseCase,
    private val endSeminarUseCase: EndSeminarUseCase,
    private val permissionChecker: RecordingPermissionChecker,
    private val clockProvider: ClockProvider,
    private val offsetCalculator: CaptureOffsetCalculator,
) : ViewModel() {

    private val seminarId: Long = savedStateHandle["seminarId"]
        ?: savedStateHandle.toRoute<ActiveSessionRoute>().seminarId
    private val permissionDenied = MutableStateFlow(false)
    private val ending = MutableStateFlow(false)
    private val isCapturingPhoto = MutableStateFlow(false)
    private val actionMessage = MutableStateFlow<String?>(null)

    private val coreSnapshot = combine(
        seminarRepository.observeSeminarDetail(seminarId),
        recordingRepository.observeLatestRecordingForSeminar(seminarId),
        timelineRepository.observeTimelineEvents(seminarId),
        runtimeStateProvider.state,
    ) { detail, recording, timelineEvents, runtimeState ->
        ActiveSessionCoreSnapshot(
            detail = detail,
            recording = recording,
            timelineEvents = timelineEvents,
            runtimeState = runtimeState,
        )
    }

    val uiState: StateFlow<ActiveSessionUiState> = combine(
        coreSnapshot,
        permissionDenied,
        ending,
        isCapturingPhoto,
        actionMessage,
    ) { core, isPermissionDenied, isEnding, isPhotoCaptureInFlight, message ->
        ActiveSessionSnapshot(
            core = core,
            isPermissionDenied = isPermissionDenied,
            isEnding = isEnding,
            isPhotoCaptureInFlight = isPhotoCaptureInFlight,
            actionMessage = message,
        )
    }
        .mapLatest { snapshot -> snapshot.toUiState(seminarRepository.getActiveSeminarSessionState()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActiveSessionUiState.Loading)

    private val _events = MutableSharedFlow<ActiveSessionEvent>(replay = 0)
    val events: SharedFlow<ActiveSessionEvent> = _events.asSharedFlow()

    fun onAction(action: ActiveSessionAction) {
        when (action) {
            ActiveSessionAction.ResumeRecording -> resumeRecording()
            ActiveSessionAction.MarkMoment -> addMark()
            ActiveSessionAction.CaptureSlide -> requestPhotoCapture()
            ActiveSessionAction.UndoLastPhoto -> undoLastPhoto()
            ActiveSessionAction.RetakeLastPhoto -> retakeLastPhoto()
            is ActiveSessionAction.AddQuestion -> addTextEvent(TimelineEventType.QUESTION, action.text)
            is ActiveSessionAction.AddNote -> addTextEvent(TimelineEventType.NOTE, action.text)
            is ActiveSessionAction.PhotoCaptureCompleted -> completePhotoCapture(action.relativePath)
            is ActiveSessionAction.PhotoCaptureFailed -> failPhotoCapture(action.relativePath, action.message)
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

    private fun addMark() {
        viewModelScope.launch {
            val anchor = currentCaptureAnchor() ?: return@launch
            val mark = timelineRepository.addMark(
                seminarId = seminarId,
                recordingId = anchor.recordingId,
                offsetMs = offsetCalculator.offsetFrom(anchor, clockProvider.now()),
            )
            clipRepository.createPendingClipForMark(mark)?.let { clip ->
                clipWorkScheduler.enqueueClipGeneration(clip.id)
            }
            actionMessage.value = "Mark saved."
        }
    }

    private fun addTextEvent(type: TimelineEventType, text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) {
            actionMessage.value = "Text cannot be empty."
            return
        }
        viewModelScope.launch {
            val anchor = currentCaptureAnchor() ?: return@launch
            when (type) {
                TimelineEventType.QUESTION -> timelineRepository.addQuestion(
                    seminarId = seminarId,
                    recordingId = anchor.recordingId,
                    offsetMs = offsetCalculator.offsetFrom(anchor, clockProvider.now()),
                    text = trimmed,
                )
                TimelineEventType.NOTE -> timelineRepository.addNote(
                    seminarId = seminarId,
                    recordingId = anchor.recordingId,
                    offsetMs = offsetCalculator.offsetFrom(anchor, clockProvider.now()),
                    text = trimmed,
                )
                TimelineEventType.MARK,
                TimelineEventType.PHOTO,
                -> return@launch
            }
            actionMessage.value = if (type == TimelineEventType.QUESTION) "Question saved." else "Note saved."
        }
    }

    private fun requestPhotoCapture() {
        viewModelScope.launch {
            val current = uiState.value as? ActiveSessionUiState.Recording ?: return@launch
            isCapturingPhoto.value = true
            val output = mediaStorageManager.createPhotoOutputFile(current.detail.id, clockProvider.now())
            _events.emit(ActiveSessionEvent.CapturePhoto(output.file.absolutePath, output.relativePath))
        }
    }

    private fun completePhotoCapture(relativePath: String) {
        viewModelScope.launch {
            val anchor = currentCaptureAnchor()
            if (anchor == null) {
                mediaStorageManager.deleteRelativeFile(relativePath)
                isCapturingPhoto.value = false
                actionMessage.value = "Photo was discarded because the active session is unavailable."
                return@launch
            }
            timelineRepository.addPhoto(
                seminarId = seminarId,
                recordingId = anchor.recordingId,
                offsetMs = offsetCalculator.offsetFrom(anchor, clockProvider.now()),
                photoPath = relativePath,
            )
            isCapturingPhoto.value = false
            actionMessage.value = "Slide photo saved."
        }
    }

    private fun failPhotoCapture(relativePath: String, message: String) {
        viewModelScope.launch {
            mediaStorageManager.deleteRelativeFile(relativePath)
            isCapturingPhoto.value = false
            actionMessage.value = "Photo capture failed: $message"
        }
    }

    private fun undoLastPhoto() {
        viewModelScope.launch {
            val lastPhoto = (uiState.value as? ActiveSessionUiState.Recording)?.lastPhoto?.event ?: return@launch
            timelineRepository.deleteEvent(lastPhoto.id)
            actionMessage.value = "Last photo removed."
        }
    }

    private fun retakeLastPhoto() {
        viewModelScope.launch {
            val lastPhoto = (uiState.value as? ActiveSessionUiState.Recording)?.lastPhoto?.event
            if (lastPhoto != null) {
                timelineRepository.deleteEvent(lastPhoto.id)
            }
            requestPhotoCapture()
        }
    }

    private fun currentCaptureAnchor(): CaptureOffsetAnchor? {
        val current = uiState.value as? ActiveSessionUiState.Recording ?: return null
        return CaptureOffsetAnchor(
            recordingId = current.recording?.id,
            startedAt = current.elapsedStartedAt,
        )
    }

    private suspend fun ActiveSessionSnapshot.toUiState(
        activeState: ActiveSeminarSessionState,
    ): ActiveSessionUiState {
        val detail = core.detail
        val recording = core.recording
        val timelineEvents = core.timelineEvents
        val runtimeState = core.runtimeState

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
                mode = CaptureSessionMode.RECORD_AND_PHOTOS,
                elapsedStartedAt = runtimeState.recording.startedAt,
                statusText = "Recording",
                notificationPermissionGranted = null,
                eventCount = timelineEvents.size,
                lastPhoto = latestPhotoFeedback(timelineEvents),
                isCapturingPhoto = isPhotoCaptureInFlight,
                actionMessage = actionMessage,
            )
        }

        if (runtimeState is RecordingServiceState.Starting && runtimeState.seminarId == seminarId) {
            return ActiveSessionUiState.Recording(
                detail = currentDetail,
                recording = recording,
                mode = CaptureSessionMode.RECORD_AND_PHOTOS,
                elapsedStartedAt = recording?.startedAt ?: currentDetail.sessionStartedAt,
                statusText = "Starting recorder",
                notificationPermissionGranted = null,
                eventCount = timelineEvents.size,
                lastPhoto = latestPhotoFeedback(timelineEvents),
                isCapturingPhoto = isPhotoCaptureInFlight,
                actionMessage = actionMessage,
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

        if (recording != null) {
            return ActiveSessionUiState.RecoveryRequired(
                detail = currentDetail,
                title = "Recording interrupted",
                message = "The previous recording stopped unexpectedly. Existing files and records are preserved, and there is no live recording right now.",
                canResume = true,
                canEnd = true,
            )
        }

        if (currentDetail.status == SeminarStatus.ACTIVE) {
            return ActiveSessionUiState.Recording(
                detail = currentDetail,
                recording = null,
                mode = CaptureSessionMode.PHOTOS_ONLY,
                elapsedStartedAt = currentDetail.sessionStartedAt,
                statusText = "Photos only",
                notificationPermissionGranted = null,
                eventCount = timelineEvents.size,
                lastPhoto = latestPhotoFeedback(timelineEvents),
                isCapturingPhoto = isPhotoCaptureInFlight,
                actionMessage = actionMessage,
            )
        }

        return ActiveSessionUiState.Failed(
            title = "Cannot open active session",
            message = "Only active seminars can use the live capture route.",
            canRetry = false,
        )
    }

    private data class ActiveSessionCoreSnapshot(
        val detail: SeminarDetail?,
        val recording: com.yuukias.seminararc.domain.model.RecordingSession?,
        val timelineEvents: List<TimelineEvent>,
        val runtimeState: RecordingServiceState,
    )

    private data class ActiveSessionSnapshot(
        val core: ActiveSessionCoreSnapshot,
        val isPermissionDenied: Boolean,
        val isEnding: Boolean,
        val isPhotoCaptureInFlight: Boolean,
        val actionMessage: String?,
    )

    private suspend fun latestPhotoFeedback(events: List<TimelineEvent>): LastPhotoFeedback? {
        val latestPhoto = events.lastOrNull { event -> event.type == TimelineEventType.PHOTO && event.photoPath != null }
            ?: return null
        val absolutePath = latestPhoto.photoPath
            ?.let { path -> mediaStorageManager.resolveReadableRelativeFile(path) }
            ?.absolutePath
        return LastPhotoFeedback(latestPhoto, absolutePath)
    }
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
