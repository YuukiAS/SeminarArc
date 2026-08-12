package com.yuukias.seminararc.domain.model

import java.time.Instant

data class RecordingSession(
    val id: Long,
    val seminarId: Long,
    val filePath: String,
    val startedAt: Instant,
    val endedAt: Instant?,
    val durationMs: Long?,
    val state: RecordingState,
    val errorMessage: String?,
)

sealed interface BeginRecordingResult {
    data class Started(
        val recording: RecordingSession,
        val seminarTitle: String,
    ) : BeginRecordingResult

    data class AlreadyRecording(
        val recording: RecordingSession,
        val seminarTitle: String,
    ) : BeginRecordingResult

    data class AnotherSeminarActive(
        val requestedSeminarId: Long,
        val activeSession: ActiveSeminarSession,
    ) : BeginRecordingResult

    data class RecoveryRequired(
        val requestedSeminarId: Long,
        val reason: SeminarSessionRecoveryReason,
        val activeSessions: List<ActiveSeminarSession>,
    ) : BeginRecordingResult

    data class CannotStart(
        val seminarId: Long,
        val reason: String,
    ) : BeginRecordingResult
}

sealed interface CompleteRecordingResult {
    data class Completed(val recording: RecordingSession) : CompleteRecordingResult
    data class NotRecording(val recordingId: Long) : CompleteRecordingResult
}

sealed interface FailRecordingResult {
    data class Failed(val recording: RecordingSession) : FailRecordingResult
    data class NotFound(val recordingId: Long) : FailRecordingResult
}

sealed interface StartSeminarRecordingResult {
    data class Started(
        val seminarId: Long,
        val notificationPermissionGranted: Boolean,
    ) : StartSeminarRecordingResult

    data class AudioPermissionDenied(val seminarId: Long) : StartSeminarRecordingResult

    data class AnotherSeminarActive(
        val requestedSeminarId: Long,
        val activeSession: ActiveSeminarSession,
    ) : StartSeminarRecordingResult

    data class RecoveryRequired(
        val requestedSeminarId: Long,
        val reason: SeminarSessionRecoveryReason,
    ) : StartSeminarRecordingResult

    data class CannotStart(
        val seminarId: Long,
        val reason: String,
    ) : StartSeminarRecordingResult
}

sealed interface RecordingServiceState {
    data object Idle : RecordingServiceState
    data class Starting(val seminarId: Long) : RecordingServiceState
    data class Recording(val recording: RecordingSession, val seminarTitle: String) : RecordingServiceState
    data class Stopping(val recordingId: Long) : RecordingServiceState
    data class Completed(val recording: RecordingSession) : RecordingServiceState
    data class Failed(val seminarId: Long?, val recordingId: Long?, val message: String) : RecordingServiceState
}

