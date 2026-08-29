package com.yuukias.seminararc.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.yuukias.seminararc.data.storage.MediaStorageManager
import com.yuukias.seminararc.domain.model.RecordingSession
import com.yuukias.seminararc.domain.model.RecordingState
import com.yuukias.seminararc.domain.model.StartSeminarSessionResult
import com.yuukias.seminararc.domain.model.StartSeminarRecordingResult
import com.yuukias.seminararc.domain.repository.RecordingRepository
import com.yuukias.seminararc.domain.repository.SeminarRepository
import com.yuukias.seminararc.domain.repository.ExportShareResult
import com.yuukias.seminararc.domain.repository.ExportWriteResult
import com.yuukias.seminararc.domain.repository.SeminarExportRepository
import com.yuukias.seminararc.domain.usecase.StartSeminarRecordingUseCase
import com.yuukias.seminararc.media.playback.RecordingPlaybackController
import com.yuukias.seminararc.media.playback.RecordingPlaybackControllerState
import com.yuukias.seminararc.ui.navigation.SeminarDetailRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

sealed interface SeminarDetailEvent {
    data object Deleted : SeminarDetailEvent
    data class OpenActiveSession(val seminarId: Long) : SeminarDetailEvent
    data class OpenTimeline(val seminarId: Long) : SeminarDetailEvent
    data class ShareText(val text: String, val mimeType: String, val title: String) : SeminarDetailEvent
    data class ShareFile(val uriString: String, val mimeType: String, val title: String) : SeminarDetailEvent
}

@HiltViewModel
class SeminarDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val seminarRepository: SeminarRepository,
    private val recordingRepository: RecordingRepository,
    private val exportRepository: SeminarExportRepository,
    private val mediaStorageManager: MediaStorageManager,
    private val playbackController: RecordingPlaybackController,
    private val startSeminarRecordingUseCase: StartSeminarRecordingUseCase,
) : ViewModel() {

    private val seminarId: Long = savedStateHandle["seminarId"] ?: savedStateHandle.toRoute<SeminarDetailRoute>().seminarId
    private val _uiState = MutableStateFlow<SeminarDetailUiState>(SeminarDetailUiState.Loading)
    val uiState = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<SeminarDetailEvent>(replay = 0)
    val events: SharedFlow<SeminarDetailEvent> = _events.asSharedFlow()

    private var currentPlayableFile: File? = null
    private var currentPlayableDurationMs: Long? = null

    init {
        viewModelScope.launch {
            combine(
                seminarRepository.observeSeminarDetail(seminarId),
                recordingRepository.observeRecordingsForSeminar(seminarId),
                playbackController.state,
            ) { detail, recordings, playback ->
                DetailStateInputs(detail, recordings, playback)
            }.collectLatest { inputs ->
                val detail = inputs.detail
                detail?.let {
                    val current = _uiState.value as? SeminarDetailUiState.Ready
                    _uiState.value = SeminarDetailUiState.Ready(
                        detail = it,
                        isDeleting = current?.isDeleting ?: false,
                        showDeleteDialog = current?.showDeleteDialog ?: false,
                        isStartingRecording = current?.isStartingRecording ?: false,
                        isExporting = current?.isExporting ?: false,
                        exportMessage = current?.exportMessage,
                        recordingErrorMessage = current?.recordingErrorMessage,
                        recordingPlayback = resolvePlaybackUiState(
                            recordings = inputs.recordings,
                            controllerState = inputs.playback,
                        ),
                    )
                }
            }
        }
    }

    fun onFavoriteToggle() {
        val current = _uiState.value as? SeminarDetailUiState.Ready ?: return
        viewModelScope.launch {
            seminarRepository.setFavorite(current.detail.id, !current.detail.isFavorite)
        }
    }

    fun onRatingSelected(rating: Int) {
        val current = _uiState.value as? SeminarDetailUiState.Ready ?: return
        viewModelScope.launch {
            seminarRepository.setRating(current.detail.id, rating)
        }
    }

    fun onDeleteDialogChanged(show: Boolean) {
        val current = _uiState.value as? SeminarDetailUiState.Ready ?: return
        _uiState.value = current.copy(showDeleteDialog = show)
    }

    fun onDeleteConfirmed() {
        val current = _uiState.value as? SeminarDetailUiState.Ready ?: return
        viewModelScope.launch {
            _uiState.value = current.copy(isDeleting = true)
            seminarRepository.deleteSeminar(current.detail.id)
            _events.emit(SeminarDetailEvent.Deleted)
        }
    }

    fun onStartRecordingClicked() {
        val current = _uiState.value as? SeminarDetailUiState.Ready ?: return
        viewModelScope.launch {
            _uiState.value = current.copy(isStartingRecording = true, recordingErrorMessage = null)
            val result = startSeminarRecordingUseCase(current.detail.id)
            val latest = _uiState.value as? SeminarDetailUiState.Ready ?: return@launch
            when (result) {
                is StartSeminarRecordingResult.Started -> {
                    _uiState.value = latest.copy(isStartingRecording = false, recordingErrorMessage = null)
                    _events.emit(SeminarDetailEvent.OpenActiveSession(result.seminarId))
                }
                is StartSeminarRecordingResult.AnotherSeminarActive -> {
                    _uiState.value = latest.copy(isStartingRecording = false, recordingErrorMessage = null)
                    _events.emit(SeminarDetailEvent.OpenActiveSession(result.activeSession.seminarId))
                }
                else -> {
                    _uiState.value = latest.copy(
                        isStartingRecording = false,
                        recordingErrorMessage = result.toMessage(),
                    )
                }
            }
        }
    }

    fun onStartPhotosOnlyClicked() {
        val current = _uiState.value as? SeminarDetailUiState.Ready ?: return
        viewModelScope.launch {
            _uiState.value = current.copy(isStartingRecording = true, recordingErrorMessage = null)
            val result = seminarRepository.startSeminarSession(current.detail.id)
            val latest = _uiState.value as? SeminarDetailUiState.Ready ?: return@launch
            when (result) {
                is StartSeminarSessionResult.Started -> {
                    _uiState.value = latest.copy(isStartingRecording = false, recordingErrorMessage = null)
                    _events.emit(SeminarDetailEvent.OpenActiveSession(result.session.seminarId))
                }
                is StartSeminarSessionResult.AlreadyActive -> {
                    _uiState.value = latest.copy(isStartingRecording = false, recordingErrorMessage = null)
                    _events.emit(SeminarDetailEvent.OpenActiveSession(result.session.seminarId))
                }
                is StartSeminarSessionResult.AnotherSeminarActive -> {
                    _uiState.value = latest.copy(isStartingRecording = false, recordingErrorMessage = null)
                    _events.emit(SeminarDetailEvent.OpenActiveSession(result.activeSession.seminarId))
                }
                is StartSeminarSessionResult.RecoveryRequired -> {
                    _uiState.value = latest.copy(
                        isStartingRecording = false,
                        recordingErrorMessage = "Session recovery is required before starting: ${result.reason}.",
                    )
                }
                is StartSeminarSessionResult.NotFound -> {
                    _uiState.value = latest.copy(
                        isStartingRecording = false,
                        recordingErrorMessage = "Seminar was not found.",
                    )
                }
                is StartSeminarSessionResult.CannotStart -> {
                    _uiState.value = latest.copy(
                        isStartingRecording = false,
                        recordingErrorMessage = "Only draft seminars can start. Current status is ${result.status}.",
                    )
                }
            }
        }
    }

    fun onOpenTimelineClicked() {
        _events.tryEmit(SeminarDetailEvent.OpenTimeline(seminarId))
    }

    fun onMarkdownDestinationSelected(uriString: String) {
        exportToUri { exportRepository.writeMarkdown(seminarId, uriString) }
    }

    fun onZipDestinationSelected(uriString: String) {
        exportToUri { exportRepository.writeZip(seminarId, uriString) }
    }

    fun onShareMarkdownClicked() {
        share { exportRepository.prepareMarkdownShare(seminarId) }
    }

    fun onShareZipClicked() {
        share { exportRepository.prepareZipShare(seminarId) }
    }

    fun onPlaybackPlayPauseClicked() {
        when ((_uiState.value as? SeminarDetailUiState.Ready)?.recordingPlayback) {
            is RecordingPlaybackUiState.Playing -> playbackController.pause()
            is RecordingPlaybackUiState.Preparing -> playbackController.pause()
            is RecordingPlaybackUiState.Ready,
            is RecordingPlaybackUiState.Ended,
            is RecordingPlaybackUiState.PlaybackError,
            -> {
                val file = currentPlayableFile ?: return
                playbackController.prepare(file, currentPlayableDurationMs)
                playbackController.play()
            }
            else -> Unit
        }
    }

    private fun exportToUri(block: suspend () -> ExportWriteResult) {
        val current = _uiState.value as? SeminarDetailUiState.Ready ?: return
        viewModelScope.launch {
            _uiState.value = current.copy(isExporting = true, exportMessage = "Exporting...")
            val latest = _uiState.value as? SeminarDetailUiState.Ready ?: return@launch
            _uiState.value = when (val result = block()) {
                ExportWriteResult.Written -> latest.copy(isExporting = false, exportMessage = "Export complete.")
                is ExportWriteResult.Failed -> latest.copy(isExporting = false, exportMessage = result.message)
            }
        }
    }

    private fun share(block: suspend () -> ExportShareResult) {
        val current = _uiState.value as? SeminarDetailUiState.Ready ?: return
        viewModelScope.launch {
            _uiState.value = current.copy(isExporting = true, exportMessage = "Preparing share...")
            when (val result = block()) {
                is ExportShareResult.TextReady -> {
                    _uiState.value = (_uiState.value as? SeminarDetailUiState.Ready)
                        ?.copy(isExporting = false, exportMessage = "Share sheet ready.")
                        ?: return@launch
                    _events.emit(SeminarDetailEvent.ShareText(result.text, result.mimeType, result.title))
                }
                is ExportShareResult.Ready -> {
                    _uiState.value = (_uiState.value as? SeminarDetailUiState.Ready)
                        ?.copy(isExporting = false, exportMessage = "Share sheet ready.")
                        ?: return@launch
                    _events.emit(SeminarDetailEvent.ShareFile(result.uriString, result.mimeType, result.title))
                }
                is ExportShareResult.Failed -> {
                    _uiState.value = (_uiState.value as? SeminarDetailUiState.Ready)
                        ?.copy(isExporting = false, exportMessage = result.message)
                        ?: return@launch
                }
            }
        }
    }

    fun onPlaybackSeek(positionMs: Long) {
        val file = currentPlayableFile ?: return
        val playback = (_uiState.value as? SeminarDetailUiState.Ready)?.recordingPlayback ?: return
        if (playback is RecordingPlaybackUiState.Ready ||
            playback is RecordingPlaybackUiState.Playing ||
            playback is RecordingPlaybackUiState.Ended ||
            playback is RecordingPlaybackUiState.PlaybackError
        ) {
            playbackController.prepare(file, currentPlayableDurationMs)
            playbackController.seekTo(positionMs)
        }
    }

    fun onPlaybackSurfaceDisposed() {
        playbackController.release()
    }

    override fun onCleared() {
        playbackController.release()
        super.onCleared()
    }

    private suspend fun resolvePlaybackUiState(
        recordings: List<RecordingSession>,
        controllerState: RecordingPlaybackControllerState,
    ): RecordingPlaybackUiState {
        val activeRecording = recordings.firstOrNull { recording -> recording.state == RecordingState.RECORDING }
        if (activeRecording != null) {
            clearPlayableFile()
            return RecordingPlaybackUiState.RecordingInProgress(
                startedAtText = activeRecording.startedAt.toString(),
            )
        }

        val completedRecordings = recordings.filter { recording -> recording.state == RecordingState.COMPLETED }
        if (completedRecordings.isNotEmpty()) {
            val playable = completedRecordings.firstNotNullOfOrNull { recording ->
                mediaStorageManager.resolveReadableRelativeFile(recording.filePath)?.let { file ->
                    PlayableRecording(recording, file)
                }
            }
            if (playable == null) {
                clearPlayableFile()
                return RecordingPlaybackUiState.MissingFile(
                    message = "Recording file is missing.",
                    durationMs = completedRecordings.first().durationMs,
                )
            }
            currentPlayableFile = playable.file
            currentPlayableDurationMs = playable.recording.durationMs
            return playable.toUiState(controllerState)
        }

        val failedRecording = recordings.firstOrNull { recording -> recording.state == RecordingState.FAILED }
        if (failedRecording != null) {
            clearPlayableFile()
            return RecordingPlaybackUiState.FailedRecording(
                message = failedRecording.errorMessage ?: "Recording did not finish normally.",
            )
        }

        clearPlayableFile()
        return RecordingPlaybackUiState.NoRecording
    }

    private fun PlayableRecording.toUiState(
        controllerState: RecordingPlaybackControllerState,
    ): RecordingPlaybackUiState {
        val fallbackDurationMs = recording.durationMs
        val filePath = file.absolutePath
        return when (controllerState) {
            RecordingPlaybackControllerState.Idle -> RecordingPlaybackUiState.Ready(
                durationMs = fallbackDurationMs,
                positionMs = 0L,
            )
            is RecordingPlaybackControllerState.Preparing -> {
                if (controllerState.filePath == filePath) {
                    RecordingPlaybackUiState.Preparing(
                        durationMs = controllerState.durationMs ?: fallbackDurationMs,
                        positionMs = controllerState.positionMs,
                    )
                } else {
                    RecordingPlaybackUiState.Ready(fallbackDurationMs, 0L)
                }
            }
            is RecordingPlaybackControllerState.Ready -> {
                if (controllerState.filePath == filePath) {
                    RecordingPlaybackUiState.Ready(
                        durationMs = controllerState.durationMs ?: fallbackDurationMs,
                        positionMs = controllerState.positionMs,
                    )
                } else {
                    RecordingPlaybackUiState.Ready(fallbackDurationMs, 0L)
                }
            }
            is RecordingPlaybackControllerState.Playing -> {
                if (controllerState.filePath == filePath) {
                    RecordingPlaybackUiState.Playing(
                        durationMs = controllerState.durationMs ?: fallbackDurationMs,
                        positionMs = controllerState.positionMs,
                    )
                } else {
                    RecordingPlaybackUiState.Ready(fallbackDurationMs, 0L)
                }
            }
            is RecordingPlaybackControllerState.Ended -> {
                if (controllerState.filePath == filePath) {
                    RecordingPlaybackUiState.Ended(
                        durationMs = controllerState.durationMs ?: fallbackDurationMs,
                        positionMs = controllerState.positionMs,
                    )
                } else {
                    RecordingPlaybackUiState.Ready(fallbackDurationMs, 0L)
                }
            }
            is RecordingPlaybackControllerState.Error -> {
                if (controllerState.filePath == filePath) {
                    RecordingPlaybackUiState.PlaybackError(
                        message = controllerState.message,
                        durationMs = controllerState.durationMs ?: fallbackDurationMs,
                        positionMs = controllerState.positionMs,
                    )
                } else {
                    RecordingPlaybackUiState.Ready(fallbackDurationMs, 0L)
                }
            }
        }
    }

    private fun clearPlayableFile() {
        currentPlayableFile = null
        currentPlayableDurationMs = null
        playbackController.release()
    }

    private fun StartSeminarRecordingResult.toMessage(): String {
        return when (this) {
            is StartSeminarRecordingResult.Started -> {
                if (notificationPermissionGranted) {
                    "Recording service started."
                } else {
                    "Recording started. Notifications are blocked, so Android may only show the foreground service in system task controls."
                }
            }
            is StartSeminarRecordingResult.AudioPermissionDenied -> {
                "Microphone permission is required before recording can start."
            }
            is StartSeminarRecordingResult.AnotherSeminarActive -> {
                "Another seminar is already active: ${activeSession.title}."
            }
            is StartSeminarRecordingResult.RecoveryRequired -> {
                "Recording recovery is required before starting: $reason."
            }
            is StartSeminarRecordingResult.CannotStart -> reason
        }
    }

    private data class DetailStateInputs(
        val detail: com.yuukias.seminararc.domain.model.SeminarDetail?,
        val recordings: List<RecordingSession>,
        val playback: RecordingPlaybackControllerState,
    )

    private data class PlayableRecording(
        val recording: RecordingSession,
        val file: File,
    )
}
