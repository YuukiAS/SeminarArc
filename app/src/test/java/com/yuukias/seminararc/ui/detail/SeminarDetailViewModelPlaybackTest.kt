package com.yuukias.seminararc.ui.detail

import androidx.lifecycle.SavedStateHandle
import com.yuukias.seminararc.data.storage.MediaStorageManager
import com.yuukias.seminararc.data.storage.RecordingOutputFile
import com.yuukias.seminararc.data.storage.StoredFile
import com.yuukias.seminararc.domain.model.AbstractAttachment
import com.yuukias.seminararc.domain.model.ActiveSeminarSession
import com.yuukias.seminararc.domain.model.ActiveSeminarSessionState
import com.yuukias.seminararc.domain.model.BeginRecordingResult
import com.yuukias.seminararc.domain.model.CompleteRecordingResult
import com.yuukias.seminararc.domain.model.CompleteSeminarResult
import com.yuukias.seminararc.domain.model.FailRecordingResult
import com.yuukias.seminararc.domain.model.RecordingSession
import com.yuukias.seminararc.domain.model.RecordingState
import com.yuukias.seminararc.domain.model.SeminarDetail
import com.yuukias.seminararc.domain.model.SeminarDraftInput
import com.yuukias.seminararc.domain.model.SeminarEditorData
import com.yuukias.seminararc.domain.model.SeminarListFilter
import com.yuukias.seminararc.domain.model.SeminarStatus
import com.yuukias.seminararc.domain.model.SeminarSummary
import com.yuukias.seminararc.domain.model.StartSeminarRecordingResult
import com.yuukias.seminararc.domain.model.StartSeminarSessionResult
import com.yuukias.seminararc.domain.repository.RecordingRepository
import com.yuukias.seminararc.domain.repository.SeminarRepository
import com.yuukias.seminararc.domain.usecase.StartSeminarRecordingUseCase
import com.yuukias.seminararc.media.playback.RecordingPlaybackController
import com.yuukias.seminararc.media.playback.RecordingPlaybackControllerState
import com.yuukias.seminararc.recording.service.RecordingPermissionChecker
import com.yuukias.seminararc.recording.service.RecordingRecoveryInitializer
import com.yuukias.seminararc.recording.service.RecordingServiceStarter
import com.yuukias.seminararc.util.ClockProvider
import java.io.File
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class SeminarDetailViewModelPlaybackTest {

    @get:Rule
    val mainDispatcherRule = DetailMainDispatcherRule()

    @Test
    fun completedRecordingWithReadableFile_isReadyForPlayback() = runTest {
        val recording = detailRecording(state = RecordingState.COMPLETED, durationMs = 90_000L)
        val viewModel = viewModel(
            recordingRepository = DetailFakeRecordingRepository(listOf(recording)),
            storage = DetailFakeMediaStorageManager(readablePaths = setOf(recording.filePath)),
        )

        advanceUntilIdle()

        val state = viewModel.readyState().recordingPlayback as RecordingPlaybackUiState.Ready
        assertEquals(90_000L, state.durationMs)
    }

    @Test
    fun completedRecordingWithMissingFile_isMissingFile() = runTest {
        val recording = detailRecording(state = RecordingState.COMPLETED, durationMs = 90_000L)
        val viewModel = viewModel(
            recordingRepository = DetailFakeRecordingRepository(listOf(recording)),
            storage = DetailFakeMediaStorageManager(readablePaths = emptySet()),
        )

        advanceUntilIdle()

        val state = viewModel.readyState().recordingPlayback as RecordingPlaybackUiState.MissingFile
        assertEquals("Recording file is missing.", state.message)
    }

    @Test
    fun failedRecording_isNotPlayable() = runTest {
        val recording = detailRecording(
            state = RecordingState.FAILED,
            errorMessage = "finalize failed",
        )
        val viewModel = viewModel(
            recordingRepository = DetailFakeRecordingRepository(listOf(recording)),
        )

        advanceUntilIdle()

        val state = viewModel.readyState().recordingPlayback as RecordingPlaybackUiState.FailedRecording
        assertTrue(state.message.contains("finalize failed"))
    }

    @Test
    fun recordingInProgress_isNotCompletedPlayback() = runTest {
        val recording = detailRecording(state = RecordingState.RECORDING)
        val viewModel = viewModel(
            recordingRepository = DetailFakeRecordingRepository(listOf(recording)),
            storage = DetailFakeMediaStorageManager(readablePaths = setOf(recording.filePath)),
        )

        advanceUntilIdle()

        assertTrue(viewModel.readyState().recordingPlayback is RecordingPlaybackUiState.RecordingInProgress)
    }

    @Test
    fun noRecording_isNoRecording() = runTest {
        val viewModel = viewModel(recordingRepository = DetailFakeRecordingRepository(emptyList()))

        advanceUntilIdle()

        assertEquals(RecordingPlaybackUiState.NoRecording, viewModel.readyState().recordingPlayback)
    }

    @Test
    fun latestFailedDoesNotHideOlderCompletedRecording() = runTest {
        val failed = detailRecording(
            id = 8L,
            state = RecordingState.FAILED,
            startedAt = Instant.parse("2026-08-13T09:00:00Z"),
            filePath = "seminars/12/recordings/failed.m4a",
        )
        val completed = detailRecording(
            id = 7L,
            state = RecordingState.COMPLETED,
            startedAt = Instant.parse("2026-08-13T08:00:00Z"),
            filePath = "seminars/12/recordings/completed.m4a",
        )
        val playback = DetailFakePlaybackController()
        val viewModel = viewModel(
            recordingRepository = DetailFakeRecordingRepository(listOf(failed, completed)),
            storage = DetailFakeMediaStorageManager(readablePaths = setOf(completed.filePath)),
            playbackController = playback,
        )

        advanceUntilIdle()
        viewModel.onPlaybackPlayPauseClicked()
        advanceUntilIdle()

        assertTrue(viewModel.readyState().recordingPlayback is RecordingPlaybackUiState.Playing)
        assertEquals("/fake/files/${completed.filePath}", playback.preparedFiles.single().absolutePath)
    }

    @Test
    fun playPause_updatesPlaybackState() = runTest {
        val recording = detailRecording(state = RecordingState.COMPLETED)
        val playback = DetailFakePlaybackController()
        val viewModel = viewModel(
            recordingRepository = DetailFakeRecordingRepository(listOf(recording)),
            storage = DetailFakeMediaStorageManager(readablePaths = setOf(recording.filePath)),
            playbackController = playback,
        )

        advanceUntilIdle()
        viewModel.onPlaybackPlayPauseClicked()
        advanceUntilIdle()
        assertTrue(viewModel.readyState().recordingPlayback is RecordingPlaybackUiState.Playing)

        viewModel.onPlaybackPlayPauseClicked()
        advanceUntilIdle()

        assertTrue(viewModel.readyState().recordingPlayback is RecordingPlaybackUiState.Ready)
    }

    @Test
    fun seek_updatesPosition() = runTest {
        val recording = detailRecording(state = RecordingState.COMPLETED, durationMs = 120_000L)
        val viewModel = viewModel(
            recordingRepository = DetailFakeRecordingRepository(listOf(recording)),
            storage = DetailFakeMediaStorageManager(readablePaths = setOf(recording.filePath)),
        )

        advanceUntilIdle()
        viewModel.onPlaybackSeek(42_000L)
        advanceUntilIdle()

        val state = viewModel.readyState().recordingPlayback as RecordingPlaybackUiState.Ready
        assertEquals(42_000L, state.positionMs)
    }

    @Test
    fun playbackError_isPlaybackErrorWithoutBreakingDetail() = runTest {
        val recording = detailRecording(state = RecordingState.COMPLETED)
        val playback = DetailFakePlaybackController()
        val viewModel = viewModel(
            recordingRepository = DetailFakeRecordingRepository(listOf(recording)),
            storage = DetailFakeMediaStorageManager(readablePaths = setOf(recording.filePath)),
            playbackController = playback,
        )

        advanceUntilIdle()
        viewModel.onPlaybackPlayPauseClicked()
        playback.emitError("decoder failed")
        advanceUntilIdle()

        val ready = viewModel.readyState()
        val state = ready.recordingPlayback as RecordingPlaybackUiState.PlaybackError
        assertEquals("Seminar 12", ready.detail.title)
        assertEquals("decoder failed", state.message)
    }

    @Test
    fun release_isIdempotent() = runTest {
        val playback = DetailFakePlaybackController()
        val viewModel = viewModel(playbackController = playback)

        advanceUntilIdle()
        val releaseCallsBefore = playback.releaseCalls
        viewModel.onPlaybackSurfaceDisposed()
        viewModel.onPlaybackSurfaceDisposed()

        assertEquals(releaseCallsBefore + 2, playback.releaseCalls)
        assertEquals(RecordingPlaybackControllerState.Idle, playback.state.value)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class DetailMainDispatcherRule : TestWatcher() {
    private val dispatcher = StandardTestDispatcher()

    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}

private fun viewModel(
    seminarRepository: DetailFakeSeminarRepository = DetailFakeSeminarRepository(completedDetail()),
    recordingRepository: DetailFakeRecordingRepository = DetailFakeRecordingRepository(emptyList()),
    storage: DetailFakeMediaStorageManager = DetailFakeMediaStorageManager(),
    playbackController: DetailFakePlaybackController = DetailFakePlaybackController(),
): SeminarDetailViewModel {
    return SeminarDetailViewModel(
        savedStateHandle = SavedStateHandle(mapOf("seminarId" to 12L)),
        seminarRepository = seminarRepository,
        recordingRepository = recordingRepository,
        mediaStorageManager = storage,
        playbackController = playbackController,
        startSeminarRecordingUseCase = StartSeminarRecordingUseCase(
            seminarRepository = seminarRepository,
            permissionChecker = DetailFakePermissionChecker(),
            recordingRecoveryInitializer = RecordingRecoveryInitializer(
                recordingRepository = recordingRepository,
                clockProvider = ClockProvider { Instant.parse("2026-08-13T10:00:00Z") },
            ),
            serviceStarter = DetailFakeServiceStarter(),
        ),
    )
}

private fun SeminarDetailViewModel.readyState(): SeminarDetailUiState.Ready {
    return uiState.value as SeminarDetailUiState.Ready
}

private class DetailFakePlaybackController : RecordingPlaybackController {
    override val state = MutableStateFlow<RecordingPlaybackControllerState>(RecordingPlaybackControllerState.Idle)
    val preparedFiles = mutableListOf<File>()
    var releaseCalls = 0
    private var currentFilePath: String? = null
    private var currentDurationMs: Long? = null
    private var currentPositionMs: Long = 0L

    override fun prepare(file: File, durationMs: Long?) {
        currentFilePath = file.absolutePath
        currentDurationMs = durationMs
        currentPositionMs = when (val current = state.value) {
            is RecordingPlaybackControllerState.Ended,
            is RecordingPlaybackControllerState.Error,
            -> 0L
            else -> currentPositionMs
        }
        preparedFiles += file
        state.value = RecordingPlaybackControllerState.Ready(
            filePath = file.absolutePath,
            durationMs = durationMs,
            positionMs = currentPositionMs,
        )
    }

    override fun play() {
        val filePath = currentFilePath ?: return
        state.value = RecordingPlaybackControllerState.Playing(
            filePath = filePath,
            durationMs = currentDurationMs,
            positionMs = currentPositionMs,
        )
    }

    override fun pause() {
        val filePath = currentFilePath ?: return
        state.value = RecordingPlaybackControllerState.Ready(
            filePath = filePath,
            durationMs = currentDurationMs,
            positionMs = currentPositionMs,
        )
    }

    override fun seekTo(positionMs: Long) {
        val filePath = currentFilePath ?: return
        currentPositionMs = positionMs
        state.value = RecordingPlaybackControllerState.Ready(
            filePath = filePath,
            durationMs = currentDurationMs,
            positionMs = currentPositionMs,
        )
    }

    override fun release() {
        releaseCalls += 1
        currentFilePath = null
        currentDurationMs = null
        currentPositionMs = 0L
        state.value = RecordingPlaybackControllerState.Idle
    }

    fun emitError(message: String) {
        state.value = RecordingPlaybackControllerState.Error(
            filePath = currentFilePath,
            durationMs = currentDurationMs,
            positionMs = currentPositionMs,
            message = message,
        )
    }
}

private class DetailFakeRecordingRepository(
    recordings: List<RecordingSession>,
) : RecordingRepository {
    private val recordingsFlow = MutableStateFlow(recordings)

    override fun observeLatestRecordingForSeminar(seminarId: Long): Flow<RecordingSession?> {
        return recordingsFlow.map { recordings -> recordings.firstOrNull() }
    }

    override fun observeRecordingsForSeminar(seminarId: Long): Flow<List<RecordingSession>> = recordingsFlow
    override suspend fun beginRecordingForActiveSeminar(seminarId: Long, filePath: String, startedAt: Instant): BeginRecordingResult = BeginRecordingResult.Started(detailRecording(), "Seminar 12")
    override suspend fun completeRecording(recordingId: Long, endedAt: Instant, durationMs: Long): CompleteRecordingResult = CompleteRecordingResult.Completed(detailRecording(state = RecordingState.COMPLETED))
    override suspend fun failRecording(recordingId: Long, endedAt: Instant, errorMessage: String): FailRecordingResult = FailRecordingResult.Failed(detailRecording(state = RecordingState.FAILED))
    override suspend fun getOpenRecordingIds(): List<Long> = emptyList()
    override suspend fun getOpenRecordingIdsForSeminar(seminarId: Long): List<Long> = emptyList()
    override suspend fun failRecordings(recordingIds: List<Long>, endedAt: Instant, errorMessage: String): Int = recordingIds.size
    override suspend fun failOpenRecordings(endedAt: Instant, errorMessage: String): Int = 0
}

private class DetailFakeMediaStorageManager(
    private val readablePaths: Set<String> = emptySet(),
) : MediaStorageManager {
    override suspend fun importAbstractPdf(seminarId: Long, sourceUri: String): StoredFile = error("Not used")
    override suspend fun createRecordingOutputFile(seminarId: Long, startedAt: Instant): RecordingOutputFile = error("Not used")
    override suspend fun createPhotoOutputFile(seminarId: Long, capturedAt: Instant): com.yuukias.seminararc.data.storage.PhotoOutputFile = error("Not used")
    override suspend fun createClipOutputFile(seminarId: Long, clipId: Long): com.yuukias.seminararc.data.storage.ClipOutputFile = error("Not used")
    override suspend fun resolveReadableRelativeFile(relativePath: String): File? {
        return if (relativePath in readablePaths) File("/fake/files/$relativePath") else null
    }
    override suspend fun deleteRelativeFile(relativePath: String) = Unit
    override suspend fun deleteSeminarMedia(seminarId: Long) = Unit
}

private class DetailFakeSeminarRepository(
    detail: SeminarDetail,
) : SeminarRepository {
    private val detailFlow = MutableStateFlow(detail)
    override fun observeSeminars(filter: SeminarListFilter, query: String): Flow<List<SeminarSummary>> = flowOf(emptyList())
    override fun observeSeminarDetail(seminarId: Long): Flow<SeminarDetail?> = detailFlow
    override suspend fun getSeminarEditorData(seminarId: Long): SeminarEditorData? = null
    override suspend fun saveSeminar(input: SeminarDraftInput): Long = input.id ?: 1L
    override suspend fun getActiveSeminarSessionState(): ActiveSeminarSessionState = ActiveSeminarSessionState.None
    override suspend fun startSeminarSession(seminarId: Long): StartSeminarSessionResult = StartSeminarSessionResult.Started(
        ActiveSeminarSession(seminarId, "Seminar $seminarId", Instant.parse("2026-08-13T08:00:00Z")),
    )
    override suspend fun completeActiveSeminar(seminarId: Long): CompleteSeminarResult = CompleteSeminarResult.Completed(seminarId)
    override suspend fun importAbstractPdf(seminarId: Long, sourceUri: String): AbstractAttachment = error("Not used")
    override suspend fun removeAbstractPdf(seminarId: Long) = Unit
    override suspend fun setFavorite(seminarId: Long, isFavorite: Boolean) = Unit
    override suspend fun setRating(seminarId: Long, rating: Int?) = Unit
    override suspend fun deleteSeminar(seminarId: Long) = Unit
}

private class DetailFakePermissionChecker : RecordingPermissionChecker {
    override fun hasRecordAudioPermission(): Boolean = true
    override fun hasPostNotificationsPermission(): Boolean = true
}

private class DetailFakeServiceStarter : RecordingServiceStarter {
    override fun start(seminarId: Long) = Unit
    override fun stop() = Unit
}

private fun completedDetail(): SeminarDetail {
    return SeminarDetail(
        id = 12L,
        title = "Seminar 12",
        speaker = "Speaker",
        affiliation = "CUHK",
        scheduledAt = Instant.parse("2026-08-13T07:00:00Z"),
        location = "Room 303",
        abstractText = "Abstract",
        abstractAttachment = null,
        status = SeminarStatus.COMPLETED,
        sessionStartedAt = Instant.parse("2026-08-13T08:00:00Z"),
        sessionEndedAt = Instant.parse("2026-08-13T09:00:00Z"),
        rating = null,
        isFavorite = false,
        photoCount = 0,
        clipCount = 0,
        recordingDurationMs = 60_000L,
        timelinePreview = emptyList(),
    )
}

private fun detailRecording(
    id: Long = 7L,
    state: RecordingState = RecordingState.RECORDING,
    filePath: String = "seminars/12/recordings/recording-$id.m4a",
    startedAt: Instant = Instant.parse("2026-08-13T08:00:00Z"),
    durationMs: Long? = 60_000L,
    errorMessage: String? = null,
): RecordingSession {
    return RecordingSession(
        id = id,
        seminarId = 12L,
        filePath = filePath,
        startedAt = startedAt,
        endedAt = if (state == RecordingState.RECORDING) null else startedAt.plusMillis(durationMs ?: 0L),
        durationMs = if (state == RecordingState.RECORDING) null else durationMs,
        state = state,
        errorMessage = errorMessage,
    )
}
