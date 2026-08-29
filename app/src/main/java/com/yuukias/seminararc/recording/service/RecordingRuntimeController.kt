package com.yuukias.seminararc.recording.service

import com.yuukias.seminararc.domain.model.RecordingServiceState
import kotlinx.coroutines.flow.StateFlow

interface RecordingRuntimeStateProvider {
    val state: StateFlow<RecordingServiceState>
}

interface RecordingRuntimeController : RecordingRuntimeStateProvider {
    fun hasLiveRecordingForSeminar(seminarId: Long): Boolean

    suspend fun stopActiveRecording(): RecordingServiceCommandResult

    suspend fun abandonActiveRecording(errorMessage: String): RecordingServiceCommandResult
}
