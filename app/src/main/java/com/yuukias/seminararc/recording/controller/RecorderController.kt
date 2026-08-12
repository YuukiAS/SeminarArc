package com.yuukias.seminararc.recording.controller

import java.io.File

sealed interface RecorderStartResult {
    data object Started : RecorderStartResult
    data class Failed(val message: String) : RecorderStartResult
}

sealed interface RecorderStopResult {
    data class Stopped(val durationMs: Long) : RecorderStopResult
    data class Failed(val message: String) : RecorderStopResult
}

interface RecorderController {
    val isRecording: Boolean

    fun start(outputFile: File): RecorderStartResult

    fun stop(): RecorderStopResult

    fun release()
}

fun interface RecorderControllerFactory {
    fun create(): RecorderController
}
