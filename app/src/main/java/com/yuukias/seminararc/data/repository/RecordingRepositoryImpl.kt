package com.yuukias.seminararc.data.repository

import com.yuukias.seminararc.data.local.DatabaseTransactionRunner
import com.yuukias.seminararc.data.local.dao.RecordingDao
import com.yuukias.seminararc.data.local.dao.SeminarDao
import com.yuukias.seminararc.data.local.entity.RecordingEntity
import com.yuukias.seminararc.data.local.entity.SeminarEntity
import com.yuukias.seminararc.domain.model.ActiveSeminarSession
import com.yuukias.seminararc.domain.model.BeginRecordingResult
import com.yuukias.seminararc.domain.model.CompleteRecordingResult
import com.yuukias.seminararc.domain.model.FailRecordingResult
import com.yuukias.seminararc.domain.model.RecordingSession
import com.yuukias.seminararc.domain.model.RecordingState
import com.yuukias.seminararc.domain.model.SeminarSessionRecoveryReason
import com.yuukias.seminararc.domain.model.SeminarStatus
import com.yuukias.seminararc.domain.repository.RecordingRepository
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RecordingRepositoryImpl @Inject constructor(
    private val seminarDao: SeminarDao,
    private val recordingDao: RecordingDao,
    private val transactionRunner: DatabaseTransactionRunner,
) : RecordingRepository {

    override fun observeLatestRecordingForSeminar(seminarId: Long): Flow<RecordingSession?> {
        return recordingDao.observeLatestRecordingForSeminar(seminarId).map { entity -> entity?.toDomain() }
    }

    override fun observeRecordingsForSeminar(seminarId: Long): Flow<List<RecordingSession>> {
        return recordingDao.observeRecordingsForSeminar(seminarId).map { entities ->
            entities.map { entity -> entity.toDomain() }
        }
    }

    override suspend fun beginRecordingForActiveSeminar(
        seminarId: Long,
        filePath: String,
        startedAt: Instant,
    ): BeginRecordingResult {
        return transactionRunner.withTransaction {
            val activeSeminars = seminarDao.getSeminarsByStatus(SeminarStatus.ACTIVE)
            val activeState = activeSeminars.toValidatedActiveState()
            if (activeState != null) {
                return@withTransaction activeState.toBeginRecordingResult(seminarId)
            }

            val activeSeminar = activeSeminars.single()
            if (activeSeminar.id != seminarId) {
                return@withTransaction BeginRecordingResult.AnotherSeminarActive(
                    requestedSeminarId = seminarId,
                    activeSession = activeSeminar.toActiveSession(),
                )
            }

            val existingRecording = recordingDao.getLatestRecordingByState(
                seminarId = seminarId,
                state = RecordingState.RECORDING,
            )
            if (existingRecording != null) {
                return@withTransaction BeginRecordingResult.StaleRecording(
                    recording = existingRecording.toDomain(),
                    seminarTitle = activeSeminar.title,
                )
            }

            val recordingId = recordingDao.insertRecording(
                RecordingEntity(
                    seminarId = seminarId,
                    filePath = filePath,
                    startedAt = startedAt,
                    endedAt = null,
                    durationMs = null,
                    state = RecordingState.RECORDING,
                    errorMessage = null,
                ),
            )
            val recording = recordingDao.getRecording(recordingId)
                ?: return@withTransaction BeginRecordingResult.CannotStart(
                    seminarId = seminarId,
                    reason = "Recording row was not readable after insert.",
                )
            BeginRecordingResult.Started(
                recording = recording.toDomain(),
                seminarTitle = activeSeminar.title,
            )
        }
    }

    override suspend fun completeRecording(
        recordingId: Long,
        endedAt: Instant,
        durationMs: Long,
    ): CompleteRecordingResult {
        return transactionRunner.withTransaction {
            val updated = recordingDao.markRecordingCompleted(
                recordingId = recordingId,
                recordingState = RecordingState.RECORDING,
                completedState = RecordingState.COMPLETED,
                endedAt = endedAt,
                durationMs = durationMs,
            )
            if (updated != 1) {
                return@withTransaction CompleteRecordingResult.NotRecording(recordingId)
            }
            val recording = recordingDao.getRecording(recordingId)
                ?: return@withTransaction CompleteRecordingResult.NotRecording(recordingId)
            CompleteRecordingResult.Completed(recording.toDomain())
        }
    }

    override suspend fun failRecording(
        recordingId: Long,
        endedAt: Instant,
        errorMessage: String,
    ): FailRecordingResult {
        return transactionRunner.withTransaction {
            val updated = recordingDao.markRecordingFailed(
                recordingId = recordingId,
                failedState = RecordingState.FAILED,
                endedAt = endedAt,
                errorMessage = errorMessage.take(MAX_ERROR_LENGTH),
            )
            if (updated != 1) {
                return@withTransaction FailRecordingResult.NotFound(recordingId)
            }
            val recording = recordingDao.getRecording(recordingId)
                ?: return@withTransaction FailRecordingResult.NotFound(recordingId)
            FailRecordingResult.Failed(recording.toDomain())
        }
    }

    override suspend fun failOpenRecordings(
        endedAt: Instant,
        errorMessage: String,
    ): Int {
        return recordingDao.markOpenRecordingsFailed(
            recordingState = RecordingState.RECORDING,
            failedState = RecordingState.FAILED,
            endedAt = endedAt,
            errorMessage = errorMessage.take(MAX_ERROR_LENGTH),
        )
    }

    override suspend fun getOpenRecordingIds(): List<Long> {
        return recordingDao.getRecordingIdsByState(RecordingState.RECORDING)
    }

    override suspend fun getOpenRecordingIdsForSeminar(seminarId: Long): List<Long> {
        return recordingDao.getRecordingIdsBySeminarAndState(
            seminarId = seminarId,
            state = RecordingState.RECORDING,
        )
    }

    override suspend fun failRecordings(
        recordingIds: List<Long>,
        endedAt: Instant,
        errorMessage: String,
    ): Int {
        if (recordingIds.isEmpty()) {
            return 0
        }
        return recordingDao.markRecordingsFailed(
            recordingIds = recordingIds,
            recordingState = RecordingState.RECORDING,
            failedState = RecordingState.FAILED,
            endedAt = endedAt,
            errorMessage = errorMessage.take(MAX_ERROR_LENGTH),
        )
    }

    private fun List<SeminarEntity>.toValidatedActiveState(): ActiveValidationFailure? {
        return when {
            isEmpty() -> ActiveValidationFailure(
                reason = SeminarSessionRecoveryReason.LOST_UPDATE,
                activeSessions = emptyList(),
            )
            size > 1 -> ActiveValidationFailure(
                reason = SeminarSessionRecoveryReason.MULTIPLE_ACTIVE_SEMINARS,
                activeSessions = map { entity -> entity.toActiveSession() },
            )
            single().sessionStartedAt == null -> ActiveValidationFailure(
                reason = SeminarSessionRecoveryReason.ACTIVE_WITHOUT_START_TIME,
                activeSessions = listOf(single().toActiveSession()),
            )
            single().sessionEndedAt != null -> ActiveValidationFailure(
                reason = SeminarSessionRecoveryReason.ACTIVE_WITH_END_TIME,
                activeSessions = listOf(single().toActiveSession()),
            )
            else -> null
        }
    }

    private fun ActiveValidationFailure.toBeginRecordingResult(seminarId: Long): BeginRecordingResult {
        return BeginRecordingResult.RecoveryRequired(
            requestedSeminarId = seminarId,
            reason = reason,
            activeSessions = activeSessions,
        )
    }

    private fun SeminarEntity.toActiveSession(): ActiveSeminarSession {
        return ActiveSeminarSession(
            seminarId = id,
            title = title,
            startedAt = sessionStartedAt,
        )
    }

    private fun RecordingEntity.toDomain(): RecordingSession {
        return RecordingSession(
            id = id,
            seminarId = seminarId,
            filePath = filePath,
            startedAt = startedAt,
            endedAt = endedAt,
            durationMs = durationMs,
            state = state,
            errorMessage = errorMessage,
        )
    }

    private data class ActiveValidationFailure(
        val reason: SeminarSessionRecoveryReason,
        val activeSessions: List<ActiveSeminarSession>,
    )

    private companion object {
        const val MAX_ERROR_LENGTH = 500
    }
}
