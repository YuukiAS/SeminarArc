package com.yuukias.seminararc.ui.timeline

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.yuukias.seminararc.data.storage.MediaStorageManager
import com.yuukias.seminararc.domain.model.RecordingSession
import com.yuukias.seminararc.domain.model.RecordingState
import com.yuukias.seminararc.domain.model.SeminarDetail
import com.yuukias.seminararc.domain.model.TimelineEvent
import com.yuukias.seminararc.domain.model.AudioClip
import com.yuukias.seminararc.domain.model.ClipState
import com.yuukias.seminararc.domain.repository.ClipRepository
import com.yuukias.seminararc.domain.repository.RecordingRepository
import com.yuukias.seminararc.domain.repository.SeminarRepository
import com.yuukias.seminararc.domain.repository.TimelineRepository
import com.yuukias.seminararc.media.playback.RecordingPlaybackController
import com.yuukias.seminararc.media.playback.RecordingPlaybackControllerState
import com.yuukias.seminararc.media.clip.ClipWorkScheduler
import com.yuukias.seminararc.ui.navigation.SeminarTimelineRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class SeminarTimelineViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val seminarRepository: SeminarRepository,
    private val recordingRepository: RecordingRepository,
    private val timelineRepository: TimelineRepository,
    private val clipRepository: ClipRepository,
    private val clipWorkScheduler: ClipWorkScheduler,
    private val mediaStorageManager: MediaStorageManager,
    private val playbackController: RecordingPlaybackController,
) : ViewModel() {

    private val seminarId: Long = savedStateHandle["seminarId"] ?: savedStateHandle.toRoute<SeminarTimelineRoute>().seminarId

    private val _events = MutableSharedFlow<SeminarTimelineEvent>(replay = 0)
    val events: SharedFlow<SeminarTimelineEvent> = _events.asSharedFlow()

    private var currentPlayable: PlayableRecording? = null

    val uiState = combine(
        seminarRepository.observeSeminarDetail(seminarId),
        timelineRepository.observeTimelineEvents(seminarId),
        clipRepository.observeClipsForSeminar(seminarId),
        recordingRepository.observeRecordingsForSeminar(seminarId),
        playbackController.state,
    ) { detail, events, clips, recordings, playback ->
        TimelineInputs(detail, events, clips, recordings, playback)
    }
        .mapLatest { inputs -> inputs.toUiState() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SeminarTimelineUiState.Loading)

    fun onPlayFromHere(event: TimelineEvent) {
        val playable = currentPlayable
        if (playable == null) {
            viewModelScope.launch { _events.emit(SeminarTimelineEvent.ShowMessage("No readable completed recording is available.")) }
            return
        }
        playbackController.prepare(playable.file, playable.recording.durationMs)
        playbackController.seekTo(event.offsetMs)
        playbackController.play()
    }

    fun onPlayClip(item: TimelineEventUiItem) {
        val path = item.absoluteClipPath
        val clip = item.clip
        if (path == null || clip == null || clip.state != ClipState.READY) {
            viewModelScope.launch { _events.emit(SeminarTimelineEvent.ShowMessage("Clip is not ready for playback.")) }
            return
        }
        val file = File(path)
        playbackController.prepare(file, clip.endOffsetMs - clip.startOffsetMs)
        playbackController.play()
    }

    fun onRetryClip(clip: AudioClip) {
        viewModelScope.launch {
            val pending = clipRepository.retryClip(clip.id)
            if (pending == null) {
                _events.emit(SeminarTimelineEvent.ShowMessage("Clip was not found."))
            } else {
                clipWorkScheduler.enqueueClipGeneration(pending.id)
                _events.emit(SeminarTimelineEvent.ShowMessage("Clip retry queued."))
            }
        }
    }

    fun onDeleteEvent(event: TimelineEvent) {
        viewModelScope.launch {
            timelineRepository.deleteEvent(event.id)
            _events.emit(SeminarTimelineEvent.ShowMessage("Timeline event deleted."))
        }
    }

    fun onPlaybackSurfaceDisposed() {
        playbackController.release()
    }

    override fun onCleared() {
        playbackController.release()
        super.onCleared()
    }

    private suspend fun TimelineInputs.toUiState(): SeminarTimelineUiState {
        val currentDetail = detail ?: return SeminarTimelineUiState.Missing(seminarId)
        val playable = recordings
            .filter { recording -> recording.state == RecordingState.COMPLETED }
            .firstNotNullOfOrNull { recording ->
                mediaStorageManager.resolveReadableRelativeFile(recording.filePath)?.let { file ->
                    PlayableRecording(recording, file)
                }
            }
        currentPlayable = playable
        val clipsByEvent = clips.associateBy { clip -> clip.sourceEventId }
        return SeminarTimelineUiState.Ready(
            detail = currentDetail,
            items = events.map { event -> event.toUiItem(clipsByEvent[event.id]) },
            canPlayFromTimeline = playable != null,
            playbackLabel = playback.toPlaybackLabel(playable?.file),
        )
    }

    private suspend fun TimelineEvent.toUiItem(clip: AudioClip?): TimelineEventUiItem {
        val path = photoPath
        val file = path?.let { mediaStorageManager.resolveReadableRelativeFile(it) }
        val clipFile = clip?.filePath?.let { mediaStorageManager.resolveReadableRelativeFile(it) }
        return TimelineEventUiItem(
            event = this,
            clip = clip,
            absoluteClipPath = clipFile?.absolutePath,
            clipMissing = clip?.filePath != null && clipFile == null,
            absolutePhotoPath = file?.absolutePath,
            photoMissing = path != null && file == null,
        )
    }

    private fun RecordingPlaybackControllerState.toPlaybackLabel(playableFile: File?): String {
        val path = playableFile?.absolutePath
        return when (this) {
            RecordingPlaybackControllerState.Idle -> if (path == null) "No recording playback" else "Recording ready"
            is RecordingPlaybackControllerState.Preparing -> if (filePath == path) "Preparing from timeline" else "Recording ready"
            is RecordingPlaybackControllerState.Ready -> if (filePath == path) "Paused at ${formatDuration(positionMs)}" else "Recording ready"
            is RecordingPlaybackControllerState.Playing -> if (filePath == path) "Playing at ${formatDuration(positionMs)}" else "Recording ready"
            is RecordingPlaybackControllerState.Ended -> if (filePath == path) "Playback ended" else "Recording ready"
            is RecordingPlaybackControllerState.Error -> message
        }
    }

    private data class TimelineInputs(
        val detail: SeminarDetail?,
        val events: List<TimelineEvent>,
        val clips: List<AudioClip>,
        val recordings: List<RecordingSession>,
        val playback: RecordingPlaybackControllerState,
    )

    private data class PlayableRecording(
        val recording: RecordingSession,
        val file: File,
    )
}

fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs.coerceAtLeast(0L) / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
