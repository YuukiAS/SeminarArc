package com.yuukias.seminararc.domain.model

import java.time.Instant

data class ActiveSeminarSession(
    val seminarId: Long,
    val title: String,
    val startedAt: Instant?,
)

enum class SeminarSessionRecoveryReason {
    ACTIVE_WITHOUT_START_TIME,
    ACTIVE_WITH_END_TIME,
    MULTIPLE_ACTIVE_SEMINARS,
    LOST_UPDATE,
}

sealed interface ActiveSeminarSessionState {
    data object None : ActiveSeminarSessionState

    data class Active(
        val session: ActiveSeminarSession,
    ) : ActiveSeminarSessionState

    data class RecoveryRequired(
        val activeSessions: List<ActiveSeminarSession>,
        val reason: SeminarSessionRecoveryReason,
    ) : ActiveSeminarSessionState
}

sealed interface StartSeminarSessionResult {
    data class Started(
        val session: ActiveSeminarSession,
    ) : StartSeminarSessionResult

    data class AlreadyActive(
        val session: ActiveSeminarSession,
    ) : StartSeminarSessionResult

    data class AnotherSeminarActive(
        val requestedSeminarId: Long,
        val activeSession: ActiveSeminarSession,
    ) : StartSeminarSessionResult

    data class RecoveryRequired(
        val requestedSeminarId: Long,
        val activeSessions: List<ActiveSeminarSession>,
        val reason: SeminarSessionRecoveryReason,
    ) : StartSeminarSessionResult

    data class CannotStart(
        val seminarId: Long,
        val status: SeminarStatus,
    ) : StartSeminarSessionResult

    data class NotFound(
        val seminarId: Long,
    ) : StartSeminarSessionResult
}

