package com.yuukias.seminararc.recording.controller

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

class AndroidMediaRecorderController(
    private val context: Context,
) : RecorderController {

    private var recorder: MediaRecorder? = null
    private var startedElapsedMs: Long? = null

    override val isRecording: Boolean
        get() = recorder != null && startedElapsedMs != null

    override fun start(outputFile: File): RecorderStartResult {
        if (isRecording) {
            return RecorderStartResult.Failed("Recorder is already running.")
        }
        return try {
            outputFile.parentFile?.mkdirs()
            val mediaRecorder = newMediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(AUDIO_BIT_RATE)
                setAudioSamplingRate(AUDIO_SAMPLE_RATE)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }
            recorder = mediaRecorder
            startedElapsedMs = SystemClock.elapsedRealtime()
            RecorderStartResult.Started
        } catch (throwable: Throwable) {
            release()
            RecorderStartResult.Failed(throwable.message ?: throwable::class.java.simpleName)
        }
    }

    override fun stop(): RecorderStopResult {
        val mediaRecorder = recorder ?: return RecorderStopResult.Failed("Recorder is not running.")
        val startedAt = startedElapsedMs ?: return RecorderStopResult.Failed("Recorder start time is missing.")
        return try {
            mediaRecorder.stop()
            val durationMs = (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L)
            release()
            RecorderStopResult.Stopped(durationMs)
        } catch (throwable: Throwable) {
            release()
            RecorderStopResult.Failed(throwable.message ?: throwable::class.java.simpleName)
        }
    }

    override fun release() {
        recorder?.runCatching { reset() }
        recorder?.runCatching { release() }
        recorder = null
        startedElapsedMs = null
    }

    @Suppress("DEPRECATION")
    private fun newMediaRecorder(): MediaRecorder {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            MediaRecorder()
        }
    }

    private companion object {
        const val AUDIO_BIT_RATE = 128_000
        const val AUDIO_SAMPLE_RATE = 44_100
    }
}

class AndroidMediaRecorderControllerFactory @Inject constructor(
    @ApplicationContext private val context: Context,
) : RecorderControllerFactory {
    override fun create(): RecorderController = AndroidMediaRecorderController(context)
}

