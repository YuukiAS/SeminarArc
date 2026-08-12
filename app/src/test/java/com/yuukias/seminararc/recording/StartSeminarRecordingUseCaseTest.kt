package com.yuukias.seminararc.recording

import com.yuukias.seminararc.domain.model.ActiveSeminarSession
import com.yuukias.seminararc.domain.model.ActiveSeminarSessionState
import com.yuukias.seminararc.domain.model.SeminarDetail
import com.yuukias.seminararc.domain.model.SeminarDraftInput
import com.yuukias.seminararc.domain.model.SeminarEditorData
import com.yuukias.seminararc.domain.model.SeminarListFilter
import com.yuukias.seminararc.domain.model.SeminarSessionRecoveryReason
import com.yuukias.seminararc.domain.model.SeminarStatus
import com.yuukias.seminararc.domain.model.SeminarSummary
import com.yuukias.seminararc.domain.model.StartSeminarRecordingResult
import com.yuukias.seminararc.domain.model.StartSeminarSessionResult
import com.yuukias.seminararc.domain.repository.SeminarRepository
import com.yuukias.seminararc.domain.usecase.StartSeminarRecordingUseCase
import com.yuukias.seminararc.recording.service.RecordingPermissionChecker
import com.yuukias.seminararc.recording.service.RecordingServiceStarter
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StartSeminarRecordingUseCaseTest {

    @Test
    fun invoke_whenAudioPermissionDenied_doesNotStartService() = runTest {
        val serviceStarter = FakeRecordingServiceStarter()
        val useCase = StartSeminarRecordingUseCase(
            seminarRepository = FakeSeminarRepository(
                startResult = StartSeminarSessionResult.Started(activeSession(12L)),
            ),
            permissionChecker = FakePermissionChecker(hasAudio = false, hasNotifications = true),
            serviceStarter = serviceStarter,
        )

        val result = useCase(12L)

        assertEquals(StartSeminarRecordingResult.AudioPermissionDenied(12L), result)
        assertEquals(emptyList<Long>(), serviceStarter.startedSeminarIds)
    }

    @Test
    fun invoke_whenSessionIsValid_startsForegroundService() = runTest {
        val serviceStarter = FakeRecordingServiceStarter()
        val useCase = StartSeminarRecordingUseCase(
            seminarRepository = FakeSeminarRepository(
                startResult = StartSeminarSessionResult.Started(activeSession(12L)),
            ),
            permissionChecker = FakePermissionChecker(hasAudio = true, hasNotifications = true),
            serviceStarter = serviceStarter,
        )

        val result = useCase(12L)

        assertEquals(StartSeminarRecordingResult.Started(12L, notificationPermissionGranted = true), result)
        assertEquals(listOf(12L), serviceStarter.startedSeminarIds)
    }

    @Test
    fun invoke_whenNotificationPermissionDenied_stillStartsService() = runTest {
        val serviceStarter = FakeRecordingServiceStarter()
        val useCase = StartSeminarRecordingUseCase(
            seminarRepository = FakeSeminarRepository(
                startResult = StartSeminarSessionResult.AlreadyActive(activeSession(12L)),
            ),
            permissionChecker = FakePermissionChecker(hasAudio = true, hasNotifications = false),
            serviceStarter = serviceStarter,
        )

        val result = useCase(12L)

        assertEquals(StartSeminarRecordingResult.Started(12L, notificationPermissionGranted = false), result)
        assertEquals(listOf(12L), serviceStarter.startedSeminarIds)
    }

    @Test
    fun invoke_whenAnotherSeminarActive_doesNotStartService() = runTest {
        val serviceStarter = FakeRecordingServiceStarter()
        val activeSession = activeSession(99L)
        val useCase = StartSeminarRecordingUseCase(
            seminarRepository = FakeSeminarRepository(
                startResult = StartSeminarSessionResult.AnotherSeminarActive(
                    requestedSeminarId = 12L,
                    activeSession = activeSession,
                ),
            ),
            permissionChecker = FakePermissionChecker(hasAudio = true, hasNotifications = true),
            serviceStarter = serviceStarter,
        )

        val result = useCase(12L)

        assertEquals(StartSeminarRecordingResult.AnotherSeminarActive(12L, activeSession), result)
        assertTrue(serviceStarter.startedSeminarIds.isEmpty())
    }

    @Test
    fun invoke_whenRecoveryRequired_doesNotStartService() = runTest {
        val serviceStarter = FakeRecordingServiceStarter()
        val useCase = StartSeminarRecordingUseCase(
            seminarRepository = FakeSeminarRepository(
                startResult = StartSeminarSessionResult.RecoveryRequired(
                    requestedSeminarId = 12L,
                    activeSessions = emptyList(),
                    reason = SeminarSessionRecoveryReason.MULTIPLE_ACTIVE_SEMINARS,
                ),
            ),
            permissionChecker = FakePermissionChecker(hasAudio = true, hasNotifications = true),
            serviceStarter = serviceStarter,
        )

        val result = useCase(12L)

        assertEquals(
            StartSeminarRecordingResult.RecoveryRequired(
                requestedSeminarId = 12L,
                reason = SeminarSessionRecoveryReason.MULTIPLE_ACTIVE_SEMINARS,
            ),
            result,
        )
        assertTrue(serviceStarter.startedSeminarIds.isEmpty())
    }
}

private class FakePermissionChecker(
    private val hasAudio: Boolean,
    private val hasNotifications: Boolean,
) : RecordingPermissionChecker {
    override fun hasRecordAudioPermission(): Boolean = hasAudio
    override fun hasPostNotificationsPermission(): Boolean = hasNotifications
}

private class FakeRecordingServiceStarter : RecordingServiceStarter {
    val startedSeminarIds = mutableListOf<Long>()

    override fun start(seminarId: Long) {
        startedSeminarIds += seminarId
    }

    override fun stop() = Unit
}

private class FakeSeminarRepository(
    private val startResult: StartSeminarSessionResult,
) : SeminarRepository {
    override fun observeSeminars(filter: SeminarListFilter, query: String): Flow<List<SeminarSummary>> = flowOf(emptyList())
    override fun observeSeminarDetail(seminarId: Long): Flow<SeminarDetail?> = flowOf(null)
    override suspend fun getSeminarEditorData(seminarId: Long): SeminarEditorData? = null
    override suspend fun saveSeminar(input: SeminarDraftInput): Long = input.id ?: 1L
    override suspend fun getActiveSeminarSessionState(): ActiveSeminarSessionState = ActiveSeminarSessionState.None
    override suspend fun startSeminarSession(seminarId: Long): StartSeminarSessionResult = startResult
    override suspend fun importAbstractPdf(seminarId: Long, sourceUri: String) = error("Not used")
    override suspend fun removeAbstractPdf(seminarId: Long) = Unit
    override suspend fun setFavorite(seminarId: Long, isFavorite: Boolean) = Unit
    override suspend fun setRating(seminarId: Long, rating: Int?) = Unit
    override suspend fun deleteSeminar(seminarId: Long) = Unit
}

private fun activeSession(seminarId: Long): ActiveSeminarSession {
    return ActiveSeminarSession(
        seminarId = seminarId,
        title = "Seminar $seminarId",
        startedAt = Instant.parse("2026-08-13T08:00:00Z"),
    )
}

