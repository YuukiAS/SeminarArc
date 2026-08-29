package com.yuukias.seminararc.media.playback

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class Media3RecordingPlaybackController @Inject constructor(
    @ApplicationContext private val context: Context,
) : RecordingPlaybackController {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow<RecordingPlaybackControllerState>(RecordingPlaybackControllerState.Idle)
    override val state: StateFlow<RecordingPlaybackControllerState> = _state.asStateFlow()

    private var player: ExoPlayer? = null
    private var positionTicker: Job? = null
    private var preparedFilePath: String? = null
    private var fallbackDurationMs: Long? = null

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            publishPlayerState()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            publishPlayerState()
            updateTicker(isPlaying)
        }

        override fun onPlayerError(error: PlaybackException) {
            positionTicker?.cancel()
            _state.value = RecordingPlaybackControllerState.Error(
                filePath = preparedFilePath,
                durationMs = currentDurationMs(),
                positionMs = currentPositionMs(),
                message = error.message ?: "Playback failed.",
            )
        }
    }

    override fun prepare(file: File, durationMs: Long?) {
        val filePath = file.absolutePath
        if (preparedFilePath == filePath && player != null) {
            fallbackDurationMs = durationMs
            publishPlayerState()
            return
        }

        releasePlayerOnly()
        preparedFilePath = filePath
        fallbackDurationMs = durationMs
        _state.value = RecordingPlaybackControllerState.Preparing(
            filePath = filePath,
            durationMs = durationMs,
            positionMs = 0L,
        )
        runCatching {
            ExoPlayer.Builder(context).build().also { exoPlayer ->
                player = exoPlayer
                exoPlayer.addListener(listener)
                exoPlayer.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
                exoPlayer.prepare()
            }
        }.onFailure { throwable ->
            _state.value = RecordingPlaybackControllerState.Error(
                filePath = filePath,
                durationMs = durationMs,
                positionMs = 0L,
                message = throwable.message ?: "Playback could not be prepared.",
            )
        }
    }

    override fun play() {
        player?.let { exoPlayer ->
            if (exoPlayer.playbackState == Player.STATE_ENDED) {
                exoPlayer.seekTo(0L)
            }
            exoPlayer.play()
            publishPlayerState()
            updateTicker(exoPlayer.isPlaying)
        }
    }

    override fun pause() {
        player?.pause()
        publishPlayerState()
        updateTicker(isPlaying = false)
    }

    override fun seekTo(positionMs: Long) {
        player?.let { exoPlayer ->
            exoPlayer.seekTo(positionMs.coerceAtLeast(0L))
            publishPlayerState()
        }
    }

    override fun release() {
        releasePlayerOnly()
        _state.value = RecordingPlaybackControllerState.Idle
    }

    private fun releasePlayerOnly() {
        positionTicker?.cancel()
        positionTicker = null
        player?.removeListener(listener)
        player?.release()
        player = null
        preparedFilePath = null
        fallbackDurationMs = null
    }

    private fun publishPlayerState() {
        val exoPlayer = player ?: return
        val filePath = preparedFilePath ?: return
        val durationMs = currentDurationMs()
        val positionMs = currentPositionMs()
        _state.value = when {
            exoPlayer.playbackState == Player.STATE_ENDED -> RecordingPlaybackControllerState.Ended(
                filePath = filePath,
                durationMs = durationMs,
                positionMs = durationMs ?: positionMs,
            )
            exoPlayer.isPlaying -> RecordingPlaybackControllerState.Playing(
                filePath = filePath,
                durationMs = durationMs,
                positionMs = positionMs,
            )
            exoPlayer.playbackState == Player.STATE_BUFFERING || exoPlayer.playbackState == Player.STATE_IDLE -> {
                RecordingPlaybackControllerState.Preparing(
                    filePath = filePath,
                    durationMs = durationMs,
                    positionMs = positionMs,
                )
            }
            else -> RecordingPlaybackControllerState.Ready(
                filePath = filePath,
                durationMs = durationMs,
                positionMs = positionMs,
            )
        }
    }

    private fun currentDurationMs(): Long? {
        val mediaDuration = player?.duration
        return mediaDuration
            ?.takeIf { duration -> duration != C.TIME_UNSET && duration >= 0L }
            ?: fallbackDurationMs
    }

    private fun currentPositionMs(): Long {
        return player?.currentPosition?.coerceAtLeast(0L) ?: 0L
    }

    private fun updateTicker(isPlaying: Boolean) {
        if (!isPlaying) {
            positionTicker?.cancel()
            positionTicker = null
            return
        }
        if (positionTicker?.isActive == true) {
            return
        }
        positionTicker = scope.launch {
            while (isActive) {
                delay(POSITION_TICK_MS)
                publishPlayerState()
            }
        }
    }

    private companion object {
        const val POSITION_TICK_MS = 500L
    }
}
