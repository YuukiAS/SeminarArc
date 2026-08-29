package com.yuukias.seminararc.recording

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.yuukias.seminararc.domain.model.AbstractAttachment
import com.yuukias.seminararc.domain.model.ActiveSeminarSession
import com.yuukias.seminararc.domain.model.ActiveSeminarSessionState
import com.yuukias.seminararc.domain.model.BeginRecordingResult
import com.yuukias.seminararc.domain.model.CompleteRecordingResult
import com.yuukias.seminararc.domain.model.CompleteSeminarResult
import com.yuukias.seminararc.domain.model.FailRecordingResult
import com.yuukias.seminararc.domain.model.RecordingServiceState
import com.yuukias.seminararc.domain.model.RecordingSession
import com.yuukias.seminararc.domain.model.RecordingState
import com.yuukias.seminararc.domain.model.SeminarDetail
import com.yuukias.seminararc.domain.model.SeminarDraftInput
import com.yuukias.seminararc.domain.model.SeminarEditorData
import com.yuukias.seminararc.domain.model.SeminarListFilter
import com.yuukias.seminararc.domain.model.SeminarSessionRecoveryReason
import com.yuukias.seminararc.domain.model.SeminarStatus
import com.yuukias.seminararc.domain.model.SeminarSummary
import com.yuukias.seminararc.domain.model.StartSeminarRecordingResult
import com.yuukias.seminararc.domain.model.StartSeminarSessionResult
import com.yuukias.seminararc.domain.repository.RecordingRepository
import com.yuukias.seminararc.domain.repository.SeminarRepository
import com.yuukias.seminararc.domain.usecase.EndSeminarUseCase
import com.yuukias.seminararc.domain.usecase.StartSeminarRecordingUseCase
import com.yuukias.seminararc.recording.service.RecordingPermissionChecker
import com.yuukias.seminararc.recording.service.RecordingRecoveryInitializer
import com.yuukias.seminararc.recording.service.RecordingRuntimeController
import com.yuukias.seminararc.recording.service.RecordingRuntimeStateProvider
import com.yuukias.seminararc.recording.service.RecordingServiceCommandResult
import com.yuukias.seminararc.recording.service.RecordingServiceStarter
import com.yuukias.seminararc.ui.session.ActiveSessionAction
import com.yuukias.seminararc.ui.session.ActiveSessionUiState
import com.yuukias.seminararc.ui.session.ActiveSessionViewModel
import com.yuukias.seminararc.util.ClockProvider
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

class ActiveSessionViewModelTest {

    @get:Rule
    val mainDispatcherRule = ActiveSessionMainDispatcherRule()

    @Test
    fun uiState_whenRuntimeIsRecording_emitsRecordingState() = runTest {
        val recording = vmActiveRecording()
        val viewModel = viewModel(
            seminarRepository = ActiveFakeSeminarRepository(
                detail = activeDetail(),
                activeState = ActiveSeminarSessionState.Active(vmActiveSession(12L)),
            ),
            recordingRepository = ActiveFakeRecordingRepository(recording),
            runtime = ActiveFakeRuntimeProvider(
                RecordingServiceState.Recording(recording, "Seminar 12"),
            ),
        )

        viewModel.uiState.test {
            assertEquals(ActiveSessionUiState.Loading, awaitItem())
            val state = awaitItem() as ActiveSessionUiState.Recording
            assertEquals(recording.startedAt, state.elapsedStartedAt)
            assertEquals("Recording", state.statusText)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun uiState_whenRecreated_usesDurableStartedAt() = runTest {
        val recording = vmActiveRecording(startedAt = Instant.parse("2026-08-13T07:30:00Z"))
        val first = viewModel(
            seminarRepository = ActiveFakeSeminarRepository(activeDetail(), ActiveSeminarSessionState.Active(vmActiveSession(12L))),
            recordingRepository = ActiveFakeRecordingRepository(recording),
            runtime = ActiveFakeRuntimeProvider(RecordingServiceState.Recording(recording, "Seminar 12")),
        )
        val second = viewModel(
            seminarRepository = ActiveFakeSeminarRepository(activeDetail(), ActiveSeminarSessionState.Active(vmActiveSession(12L))),
            recordingRepository = ActiveFakeRecordingRepository(recording),
            runtime = ActiveFakeRuntimeProvider(RecordingServiceState.Recording(recording, "Seminar 12")),
        )

        first.uiState.test {
            awaitItem()
            assertEquals(recording.startedAt, (awaitItem() as ActiveSessionUiState.Recording).elapsedStartedAt)
            cancelAndIgnoreRemainingEvents()
        }
        second.uiState.test {
            awaitItem()
            assertEquals(recording.startedAt, (awaitItem() as ActiveSessionUiState.Recording).elapsedStartedAt)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun uiState_whenAnotherSeminarActive_emitsRecoveryConflict() = runTest {
        val viewModel = viewModel(
            seminarRepository = ActiveFakeSeminarRepository(
                detail = activeDetail(),
                activeState = ActiveSeminarSessionState.Active(vmActiveSession(99L)),
            ),
        )

        viewModel.uiState.test {
            awaitItem()
            val state = awaitItem() as ActiveSessionUiState.RecoveryRequired
            assertEquals(listOf(99L), state.conflictSeminarIds)
            assertEquals(false, state.canResume)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun uiState_whenStaleActiveWithFailedRecording_emitsRecovery() = runTest {
        val failedRecording = vmActiveRecording().copy(
            state = RecordingState.FAILED,
            endedAt = Instant.parse("2026-08-13T08:05:00Z"),
            errorMessage = "process restart",
        )
        val viewModel = viewModel(
            seminarRepository = ActiveFakeSeminarRepository(
                detail = activeDetail(),
                activeState = ActiveSeminarSessionState.Active(vmActiveSession(12L)),
            ),
            recordingRepository = ActiveFakeRecordingRepository(failedRecording),
        )

        viewModel.uiState.test {
            awaitItem()
            val state = awaitItem() as ActiveSessionUiState.RecoveryRequired
            assertEquals(true, state.canResume)
            assertEquals(true, state.canEnd)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun resumeRecording_whenAudioDenied_emitsPermissionDeniedState() = runTest {
        val viewModel = viewModel(
            seminarRepository = ActiveFakeSeminarRepository(
                detail = activeDetail(),
                activeState = ActiveSeminarSessionState.Active(vmActiveSession(12L)),
            ),
            permissionChecker = ActiveFakePermissionChecker(hasAudio = false),
        )

        viewModel.uiState.test {
            awaitItem()
            viewModel.onAction(ActiveSessionAction.ResumeRecording)
            val state = awaitItem()
            assertEquals(ActiveSessionUiState.PermissionDenied(12L), state)
            cancelAndIgnoreRemainingEvents()
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ActiveSessionMainDispatcherRule : TestWatcher() {
    private val dispatcher = StandardTestDispatcher()

    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}

private fun viewModel(
    seminarRepository: ActiveFakeSeminarRepository,
    recordingRepository: ActiveFakeRecordingRepository = ActiveFakeRecordingRepository(null),
    runtime: ActiveFakeRuntimeProvider = ActiveFakeRuntimeProvider(RecordingServiceState.Idle),
    permissionChecker: ActiveFakePermissionChecker = ActiveFakePermissionChecker(hasAudio = true),
): ActiveSessionViewModel {
    val runtimeController = ActiveFakeRuntimeController(runtime.state)
    return ActiveSessionViewModel(
        savedStateHandle = SavedStateHandle(mapOf("seminarId" to 12L)),
        seminarRepository = seminarRepository,
        recordingRepository = recordingRepository,
        runtimeStateProvider = runtime,
        startSeminarRecordingUseCase = StartSeminarRecordingUseCase(
            seminarRepository = seminarRepository,
            permissionChecker = permissionChecker,
            recordingRecoveryInitializer = RecordingRecoveryInitializer(
                recordingRepository = recordingRepository,
                clockProvider = ClockProvider { Instant.parse("2026-08-13T08:00:00Z") },
            ),
            serviceStarter = ActiveFakeServiceStarter(),
        ),
        endSeminarUseCase = EndSeminarUseCase(
            seminarRepository = seminarRepository,
            recordingRepository = recordingRepository,
            runtimeController = runtimeController,
            serviceStarter = ActiveFakeServiceStarter(),
            clockProvider = ClockProvider { Instant.parse("2026-08-13T08:00:00Z") },
        ),
        permissionChecker = permissionChecker,
    )
}

private class ActiveFakeRuntimeProvider(
    initialState: RecordingServiceState,
) : RecordingRuntimeStateProvider {
    override val state = MutableStateFlow(initialState)
}

private class ActiveFakeRuntimeController(
    override val state: MutableStateFlow<RecordingServiceState>,
) : RecordingRuntimeController {
    override fun hasLiveRecordingForSeminar(seminarId: Long): Boolean = false
    override suspend fun stopActiveRecording(): RecordingServiceCommandResult = RecordingServiceCommandResult.Idle
    override suspend fun abandonActiveRecording(errorMessage: String): RecordingServiceCommandResult = RecordingServiceCommandResult.Failed(errorMessage)
}

private class ActiveFakePermissionChecker(
    private val hasAudio: Boolean,
) : RecordingPermissionChecker {
    override fun hasRecordAudioPermission(): Boolean = hasAudio
    override fun hasPostNotificationsPermission(): Boolean = true
}

private class ActiveFakeServiceStarter : RecordingServiceStarter {
    override fun start(seminarId: Long) = Unit
    override fun stop() = Unit
}

private class ActiveFakeRecordingRepository(
    recording: RecordingSession?,
) : RecordingRepository {
    private val latestRecording = MutableStateFlow(recording)
    override fun observeLatestRecordingForSeminar(seminarId: Long): Flow<RecordingSession?> = latestRecording
    override suspend fun beginRecordingForActiveSeminar(seminarId: Long, filePath: String, startedAt: Instant): BeginRecordingResult = BeginRecordingResult.Started(vmActiveRecording(), "Seminar 12")
    override suspend fun completeRecording(recordingId: Long, endedAt: Instant, durationMs: Long): CompleteRecordingResult = CompleteRecordingResult.Completed(vmActiveRecording().copy(state = RecordingState.COMPLETED))
    override suspend fun failRecording(recordingId: Long, endedAt: Instant, errorMessage: String): FailRecordingResult = FailRecordingResult.Failed(vmActiveRecording().copy(state = RecordingState.FAILED))
    override suspend fun getOpenRecordingIds(): List<Long> = emptyList()
    override suspend fun getOpenRecordingIdsForSeminar(seminarId: Long): List<Long> = emptyList()
    override suspend fun failRecordings(recordingIds: List<Long>, endedAt: Instant, errorMessage: String): Int = recordingIds.size
    override suspend fun failOpenRecordings(endedAt: Instant, errorMessage: String): Int = 0
}

private class ActiveFakeSeminarRepository(
    detail: SeminarDetail,
    private val activeState: ActiveSeminarSessionState,
) : SeminarRepository {
    private val detailFlow = MutableStateFlow(detail)
    override fun observeSeminars(filter: SeminarListFilter, query: String): Flow<List<SeminarSummary>> = flowOf(emptyList())
    override fun observeSeminarDetail(seminarId: Long): Flow<SeminarDetail?> = detailFlow
    override suspend fun getSeminarEditorData(seminarId: Long): SeminarEditorData? = null
    override suspend fun saveSeminar(input: SeminarDraftInput): Long = input.id ?: 1L
    override suspend fun getActiveSeminarSessionState(): ActiveSeminarSessionState = activeState
    override suspend fun startSeminarSession(seminarId: Long): StartSeminarSessionResult = StartSeminarSessionResult.AlreadyActive(vmActiveSession(seminarId))
    override suspend fun completeActiveSeminar(seminarId: Long): CompleteSeminarResult = CompleteSeminarResult.Completed(seminarId)
    override suspend fun importAbstractPdf(seminarId: Long, sourceUri: String): AbstractAttachment = error("Not used")
    override suspend fun removeAbstractPdf(seminarId: Long) = Unit
    override suspend fun setFavorite(seminarId: Long, isFavorite: Boolean) = Unit
    override suspend fun setRating(seminarId: Long, rating: Int?) = Unit
    override suspend fun deleteSeminar(seminarId: Long) = Unit
}

private fun activeDetail(): SeminarDetail {
    return SeminarDetail(
        id = 12L,
        title = "Seminar 12",
        speaker = "Speaker",
        affiliation = "CUHK",
        scheduledAt = Instant.parse("2026-08-13T07:00:00Z"),
        location = "Room 303",
        abstractText = null,
        abstractAttachment = null,
        status = SeminarStatus.ACTIVE,
        sessionStartedAt = Instant.parse("2026-08-13T08:00:00Z"),
        sessionEndedAt = null,
        rating = null,
        isFavorite = false,
        photoCount = 0,
        clipCount = 0,
        recordingDurationMs = null,
        timelinePreview = emptyList(),
    )
}

private fun vmActiveSession(seminarId: Long): ActiveSeminarSession {
    return ActiveSeminarSession(
        seminarId = seminarId,
        title = "Seminar $seminarId",
        startedAt = Instant.parse("2026-08-13T08:00:00Z"),
    )
}

private fun vmActiveRecording(
    startedAt: Instant = Instant.parse("2026-08-13T08:00:00Z"),
): RecordingSession {
    return RecordingSession(
        id = 7L,
        seminarId = 12L,
        filePath = "seminars/12/recordings/recording.m4a",
        startedAt = startedAt,
        endedAt = null,
        durationMs = null,
        state = RecordingState.RECORDING,
        errorMessage = null,
    )
}
