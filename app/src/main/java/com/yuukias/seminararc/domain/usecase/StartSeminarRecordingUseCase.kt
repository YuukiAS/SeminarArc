package com.yuukias.seminararc.domain.usecase

import com.yuukias.seminararc.domain.model.StartSeminarRecordingResult
import com.yuukias.seminararc.domain.model.StartSeminarSessionResult
import com.yuukias.seminararc.domain.repository.SeminarRepository
import com.yuukias.seminararc.recording.service.RecordingPermissionChecker
import com.yuukias.seminararc.recording.service.RecordingServiceStarter
import javax.inject.Inject

class StartSeminarRecordingUseCase @Inject constructor(
    private val seminarRepository: SeminarRepository,
    private val permissionChecker: RecordingPermissionChecker,
    private val serviceStarter: RecordingServiceStarter,
) {
    suspend operator fun invoke(seminarId: Long): StartSeminarRecordingResult {
        if (!permissionChecker.hasRecordAudioPermission()) {
            return StartSeminarRecordingResult.AudioPermissionDenied(seminarId)
        }

        return when (val sessionResult = seminarRepository.startSeminarSession(seminarId)) {
            is StartSeminarSessionResult.Started -> {
                serviceStarter.start(sessionResult.session.seminarId)
                StartSeminarRecordingResult.Started(
                    seminarId = sessionResult.session.seminarId,
                    notificationPermissionGranted = permissionChecker.hasPostNotificationsPermission(),
                )
            }
            is StartSeminarSessionResult.AlreadyActive -> {
                serviceStarter.start(sessionResult.session.seminarId)
                StartSeminarRecordingResult.Started(
                    seminarId = sessionResult.session.seminarId,
                    notificationPermissionGranted = permissionChecker.hasPostNotificationsPermission(),
                )
            }
            is StartSeminarSessionResult.AnotherSeminarActive -> StartSeminarRecordingResult.AnotherSeminarActive(
                requestedSeminarId = sessionResult.requestedSeminarId,
                activeSession = sessionResult.activeSession,
            )
            is StartSeminarSessionResult.RecoveryRequired -> StartSeminarRecordingResult.RecoveryRequired(
                requestedSeminarId = sessionResult.requestedSeminarId,
                reason = sessionResult.reason,
            )
            is StartSeminarSessionResult.CannotStart -> StartSeminarRecordingResult.CannotStart(
                seminarId = sessionResult.seminarId,
                reason = "Seminar status ${sessionResult.status} cannot start recording.",
            )
            is StartSeminarSessionResult.NotFound -> StartSeminarRecordingResult.CannotStart(
                seminarId = sessionResult.seminarId,
                reason = "Seminar was not found.",
            )
        }
    }
}

