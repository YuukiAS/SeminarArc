package com.yuukias.seminararc.ui.detail

sealed interface RecordingPlaybackUiState {
    data object NoRecording : RecordingPlaybackUiState

    data object Loading : RecordingPlaybackUiState

    data class RecordingInProgress(
        val startedAtText: String,
    ) : RecordingPlaybackUiState

    data class FailedRecording(
        val message: String,
    ) : RecordingPlaybackUiState

    data class MissingFile(
        val message: String,
        val durationMs: Long?,
    ) : RecordingPlaybackUiState

    data class Ready(
        val durationMs: Long?,
        val positionMs: Long,
    ) : RecordingPlaybackUiState

    data class Preparing(
        val durationMs: Long?,
        val positionMs: Long,
    ) : RecordingPlaybackUiState

    data class Playing(
        val durationMs: Long?,
        val positionMs: Long,
    ) : RecordingPlaybackUiState

    data class Ended(
        val durationMs: Long?,
        val positionMs: Long,
    ) : RecordingPlaybackUiState

    data class PlaybackError(
        val message: String,
        val durationMs: Long?,
        val positionMs: Long,
    ) : RecordingPlaybackUiState
}
