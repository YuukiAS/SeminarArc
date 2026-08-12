package com.yuukias.seminararc.recording.service

import com.yuukias.seminararc.data.storage.MediaStorageManager
import com.yuukias.seminararc.domain.model.BeginRecordingResult
import com.yuukias.seminararc.domain.model.CompleteRecordingResult
import com.yuukias.seminararc.domain.model.FailRecordingResult
import com.yuukias.seminararc.domain.model.RecordingServiceState
import com.yuukias.seminararc.domain.repository.RecordingRepository
import com.yuukias.seminararc.recording.controller.RecorderController
import com.yuukias.seminararc.recording.controller.RecorderControllerFactory
import com.yuukias.seminararc.recording.controller.RecorderStartResult
import com.yuukias.seminararc.recording.controller.RecorderStopResult
import com.yuukias.seminararc.util.ClockProvider
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class RecordingServiceCoordinator @Inject constructor(
    private val recordingRepository: RecordingRepository,
    private val mediaStorageManager: MediaStorageManager,
    private val recorderControllerFactory: RecorderControllerFactory,
    private val clockProvider: ClockProvider,
) {
    private val mutex = Mutex()
    private var activeRuntime: ActiveRuntimeRecording? = null
    private val _state = MutableStateFlow<RecordingServiceState>(RecordingServiceState.Idle)
    val state: StateFlow<RecordingServiceState> = _state

    suspend fun start(seminarId: Long): RecordingServiceCommandResult {
        return mutex.withLock {
            activeRuntime?.let { runtime ->
                return@withLock RecordingServiceCommandResult.AlreadyRunning(
                    seminarId = runtime.recording.seminarId,
                    seminarTitle = runtime.seminarTitle,
                )
            }

            _state.value = RecordingServiceState.Starting(seminarId)
            val startedAt = clockProvider.now()
            val outputFile = mediaStorageManager.createRecordingOutputFile(seminarId, startedAt)
            when (
                val beginResult = recordingRepository.beginRecordingForActiveSeminar(
                    seminarId = seminarId,
                    filePath = outputFile.relativePath,
                    startedAt = startedAt,
                )
            ) {
                is BeginRecordingResult.Started -> {
                    val controller = recorderControllerFactory.create()
                    when (val startResult = controller.start(outputFile.file)) {
                        RecorderStartResult.Started -> {
                            activeRuntime = ActiveRuntimeRecording(
                                recording = beginResult.recording,
                                seminarTitle = beginResult.seminarTitle,
                                controller = controller,
                            )
                            _state.value = RecordingServiceState.Recording(
                                recording = beginResult.recording,
                                seminarTitle = beginResult.seminarTitle,
                            )
                            RecordingServiceCommandResult.Started(
                                seminarId = seminarId,
                                seminarTitle = beginResult.seminarTitle,
                            )
                        }
                        is RecorderStartResult.Failed -> {
                            controller.release()
                            val failed = recordingRepository.failRecording(
                                recordingId = beginResult.recording.id,
                                endedAt = clockProvider.now(),
                                errorMessage = startResult.message,
                            )
                            val recordingId = (failed as? FailRecordingResult.Failed)?.recording?.id
                            _state.value = RecordingServiceState.Failed(
                                seminarId = seminarId,
                                recordingId = recordingId,
                                message = startResult.message,
                            )
                            RecordingServiceCommandResult.Failed(startResult.message)
                        }
                    }
                }
                is BeginRecordingResult.AlreadyRecording -> {
                    mediaStorageManager.deleteRelativeFile(outputFile.relativePath)
                    _state.value = RecordingServiceState.Failed(
                        seminarId = seminarId,
                        recordingId = beginResult.recording.id,
                        message = "A durable recording row is already active.",
                    )
                    RecordingServiceCommandResult.AlreadyRunning(
                        seminarId = seminarId,
                        seminarTitle = beginResult.seminarTitle,
                    )
                }
                is BeginRecordingResult.AnotherSeminarActive -> {
                    mediaStorageManager.deleteRelativeFile(outputFile.relativePath)
                    RecordingServiceCommandResult.Rejected(
                        message = "Another seminar is active: ${beginResult.activeSession.title}",
                    )
                }
                is BeginRecordingResult.RecoveryRequired -> {
                    mediaStorageManager.deleteRelativeFile(outputFile.relativePath)
                    RecordingServiceCommandResult.Rejected(
                        message = "Recording recovery is required: ${beginResult.reason}",
                    )
                }
                is BeginRecordingResult.CannotStart -> {
                    mediaStorageManager.deleteRelativeFile(outputFile.relativePath)
                    RecordingServiceCommandResult.Rejected(beginResult.reason)
                }
            }
        }
    }

    suspend fun stop(): RecordingServiceCommandResult {
        return mutex.withLock {
            val runtime = activeRuntime ?: return@withLock RecordingServiceCommandResult.Idle
            _state.value = RecordingServiceState.Stopping(runtime.recording.id)
            when (val stopResult = runtime.controller.stop()) {
                is RecorderStopResult.Stopped -> {
                    activeRuntime = null
                    when (
                        val completed = recordingRepository.completeRecording(
                            recordingId = runtime.recording.id,
                            endedAt = clockProvider.now(),
                            durationMs = stopResult.durationMs,
                        )
                    ) {
                        is CompleteRecordingResult.Completed -> {
                            _state.value = RecordingServiceState.Completed(completed.recording)
                            RecordingServiceCommandResult.Stopped
                        }
                        is CompleteRecordingResult.NotRecording -> {
                            val message = "Recording row was not in RECORDING state during stop."
                            recordingRepository.failRecording(
                                recordingId = runtime.recording.id,
                                endedAt = clockProvider.now(),
                                errorMessage = message,
                            )
                            _state.value = RecordingServiceState.Failed(
                                seminarId = runtime.recording.seminarId,
                                recordingId = runtime.recording.id,
                                message = message,
                            )
                            RecordingServiceCommandResult.Failed(message)
                        }
                    }
                }
                is RecorderStopResult.Failed -> {
                    runtime.controller.release()
                    activeRuntime = null
                    recordingRepository.failRecording(
                        recordingId = runtime.recording.id,
                        endedAt = clockProvider.now(),
                        errorMessage = stopResult.message,
                    )
                    _state.value = RecordingServiceState.Failed(
                        seminarId = runtime.recording.seminarId,
                        recordingId = runtime.recording.id,
                        message = stopResult.message,
                    )
                    RecordingServiceCommandResult.Failed(stopResult.message)
                }
            }
        }
    }

    fun releaseRuntime() {
        activeRuntime?.controller?.release()
        activeRuntime = null
        _state.value = RecordingServiceState.Idle
    }

    private data class ActiveRuntimeRecording(
        val recording: com.yuukias.seminararc.domain.model.RecordingSession,
        val seminarTitle: String,
        val controller: RecorderController,
    )
}

sealed interface RecordingServiceCommandResult {
    data class Started(val seminarId: Long, val seminarTitle: String) : RecordingServiceCommandResult
    data class AlreadyRunning(val seminarId: Long, val seminarTitle: String) : RecordingServiceCommandResult
    data object Stopped : RecordingServiceCommandResult
    data object Idle : RecordingServiceCommandResult
    data class Rejected(val message: String) : RecordingServiceCommandResult
    data class Failed(val message: String) : RecordingServiceCommandResult
}
