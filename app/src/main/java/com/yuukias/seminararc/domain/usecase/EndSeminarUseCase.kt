package com.yuukias.seminararc.domain.usecase

import com.yuukias.seminararc.domain.model.CompleteSeminarResult
import com.yuukias.seminararc.domain.model.EndSeminarResult
import com.yuukias.seminararc.domain.repository.RecordingRepository
import com.yuukias.seminararc.domain.repository.SeminarRepository
import com.yuukias.seminararc.recording.service.RecordingRuntimeController
import com.yuukias.seminararc.recording.service.RecordingServiceCommandResult
import com.yuukias.seminararc.recording.service.RecordingServiceStarter
import com.yuukias.seminararc.util.ClockProvider
import javax.inject.Inject

class EndSeminarUseCase @Inject constructor(
    private val seminarRepository: SeminarRepository,
    private val recordingRepository: RecordingRepository,
    private val runtimeController: RecordingRuntimeController,
    private val serviceStarter: RecordingServiceStarter,
    private val clockProvider: ClockProvider,
) {
    suspend operator fun invoke(seminarId: Long): EndSeminarResult {
        if (runtimeController.hasLiveRecordingForSeminar(seminarId)) {
            when (val stopResult = runtimeController.stopActiveRecording()) {
                RecordingServiceCommandResult.Stopped -> serviceStarter.stop()
                RecordingServiceCommandResult.Idle -> Unit
                is RecordingServiceCommandResult.Failed -> {
                    serviceStarter.stop()
                    return EndSeminarResult.StopFailed(seminarId, stopResult.message)
                }
                is RecordingServiceCommandResult.AlreadyRunning,
                is RecordingServiceCommandResult.RecoveryRequired,
                is RecordingServiceCommandResult.Rejected,
                is RecordingServiceCommandResult.Started -> {
                    return EndSeminarResult.StopFailed(seminarId, "Recorder returned an invalid end state.")
                }
            }
        } else {
            recordingRepository.failRecordings(
                recordingIds = recordingRepository.getOpenRecordingIdsForSeminar(seminarId),
                endedAt = clockProvider.now(),
                errorMessage = "Recording was marked failed while ending an active seminar without a live recorder.",
            )
            serviceStarter.stop()
        }

        return when (val completed = seminarRepository.completeActiveSeminar(seminarId)) {
            is CompleteSeminarResult.Completed -> EndSeminarResult.Completed(completed.seminarId)
            is CompleteSeminarResult.AlreadyCompleted -> EndSeminarResult.AlreadyCompleted(completed.seminarId)
            is CompleteSeminarResult.NotActive -> EndSeminarResult.CannotComplete(
                seminarId = completed.seminarId,
                reason = "Seminar status ${completed.status} cannot be completed from the active session.",
            )
            is CompleteSeminarResult.NotFound -> EndSeminarResult.NotFound(completed.seminarId)
            is CompleteSeminarResult.LostUpdate -> EndSeminarResult.CannotComplete(
                seminarId = completed.seminarId,
                reason = "Seminar completion lost its ACTIVE state before the update could commit.",
            )
        }
    }
}
