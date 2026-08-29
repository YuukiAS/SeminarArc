package com.yuukias.seminararc.media.playback

import java.io.File
import kotlinx.coroutines.flow.StateFlow

sealed interface RecordingPlaybackControllerState {
    data object Idle : RecordingPlaybackControllerState

    data class Preparing(
        val filePath: String,
        val durationMs: Long?,
        val positionMs: Long,
    ) : RecordingPlaybackControllerState

    data class Ready(
        val filePath: String,
        val durationMs: Long?,
        val positionMs: Long,
    ) : RecordingPlaybackControllerState

    data class Playing(
        val filePath: String,
        val durationMs: Long?,
        val positionMs: Long,
    ) : RecordingPlaybackControllerState

    data class Ended(
        val filePath: String,
        val durationMs: Long?,
        val positionMs: Long,
    ) : RecordingPlaybackControllerState

    data class Error(
        val filePath: String?,
        val durationMs: Long?,
        val positionMs: Long,
        val message: String,
    ) : RecordingPlaybackControllerState
}

interface RecordingPlaybackController {
    val state: StateFlow<RecordingPlaybackControllerState>

    fun prepare(file: File, durationMs: Long?)
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun release()
}
