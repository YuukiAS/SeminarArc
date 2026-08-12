package com.yuukias.seminararc.recording

import com.yuukias.seminararc.data.storage.MediaStorageManager
import com.yuukias.seminararc.data.storage.RecordingOutputFile
import com.yuukias.seminararc.data.storage.StoredFile
import com.yuukias.seminararc.domain.model.BeginRecordingResult
import com.yuukias.seminararc.domain.model.CompleteRecordingResult
import com.yuukias.seminararc.domain.model.FailRecordingResult
import com.yuukias.seminararc.domain.model.RecordingSession
import com.yuukias.seminararc.domain.model.RecordingState
import com.yuukias.seminararc.domain.repository.RecordingRepository
import com.yuukias.seminararc.recording.controller.RecorderController
import com.yuukias.seminararc.recording.controller.RecorderControllerFactory
import com.yuukias.seminararc.recording.controller.RecorderStartResult
import com.yuukias.seminararc.recording.controller.RecorderStopResult
import com.yuukias.seminararc.recording.notification.SeminarRecordingNotificationContract
import com.yuukias.seminararc.recording.notification.SeminarRecordingNotificationSpecFactory
import com.yuukias.seminararc.recording.service.RecordingServiceCommandResult
import com.yuukias.seminararc.recording.service.RecordingServiceCoordinator
import com.yuukias.seminararc.util.ClockProvider
import java.io.File
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingServiceCoordinatorTest {

    @Test
    fun start_whenRepositoryAllowsRecording_startsRecorderAndKeepsDurableState() = runTest {
        val repository = FakeRecordingRepository()
        val storage = FakeMediaStorageManager()
        val controller = FakeRecorderController()
        val coordinator = coordinator(repository, storage, FakeRecorderControllerFactory(controller))

        val result = coordinator.start(12L)

        assertEquals(RecordingServiceCommandResult.Started(12L, "Seminar 12"), result)
        assertEquals(listOf(12L), storage.createdForSeminarIds)
        assertTrue(storage.createdRelativePaths.single().startsWith("seminars/12/recordings/"))
        assertEquals(1, repository.beginCalls)
        assertEquals(storage.createdRelativePaths.single(), repository.recording.filePath)
        assertEquals(1, controller.startCalls)
    }

    @Test
    fun start_whenCalledTwice_doesNotCreateSecondRecorderOrRecordingRow() = runTest {
        val repository = FakeRecordingRepository()
        val storage = FakeMediaStorageManager()
        val controller = FakeRecorderController()
        val coordinator = coordinator(repository, storage, FakeRecorderControllerFactory(controller))

        coordinator.start(12L)
        val duplicate = coordinator.start(12L)

        assertEquals(RecordingServiceCommandResult.AlreadyRunning(12L, "Seminar 12"), duplicate)
        assertEquals(1, repository.beginCalls)
        assertEquals(1, storage.createdForSeminarIds.size)
        assertEquals(1, controller.startCalls)
    }

    @Test
    fun start_whenRecorderFails_marksRecordingFailed() = runTest {
        val repository = FakeRecordingRepository()
        val controller = FakeRecorderController(startResult = RecorderStartResult.Failed("mic failed"))
        val coordinator = coordinator(repository, FakeMediaStorageManager(), FakeRecorderControllerFactory(controller))

        val result = coordinator.start(12L)

        assertEquals(RecordingServiceCommandResult.Failed("mic failed"), result)
        assertEquals(RecordingState.FAILED, repository.recording.state)
        assertEquals("mic failed", repository.recording.errorMessage)
        assertEquals(1, controller.releaseCalls)
    }

    @Test
    fun stop_whenRecorderStops_marksRecordingCompleted() = runTest {
        val repository = FakeRecordingRepository()
        val controller = FakeRecorderController(stopResult = RecorderStopResult.Stopped(durationMs = 42_000L))
        val coordinator = coordinator(repository, FakeMediaStorageManager(), FakeRecorderControllerFactory(controller))

        coordinator.start(12L)
        val result = coordinator.stop()

        assertEquals(RecordingServiceCommandResult.Stopped, result)
        assertEquals(RecordingState.COMPLETED, repository.recording.state)
        assertEquals(42_000L, repository.recording.durationMs)
        assertEquals(1, controller.stopCalls)
    }

    @Test
    fun stop_whenRecorderStopFails_marksRecordingFailed() = runTest {
        val repository = FakeRecordingRepository()
        val controller = FakeRecorderController(stopResult = RecorderStopResult.Failed("stop failed"))
        val coordinator = coordinator(repository, FakeMediaStorageManager(), FakeRecorderControllerFactory(controller))

        coordinator.start(12L)
        val result = coordinator.stop()

        assertEquals(RecordingServiceCommandResult.Failed("stop failed"), result)
        assertEquals(RecordingState.FAILED, repository.recording.state)
        assertEquals("stop failed", repository.recording.errorMessage)
    }

    @Test
    fun stop_whenIdle_isIdempotent() = runTest {
        val coordinator = coordinator(
            repository = FakeRecordingRepository(),
            storage = FakeMediaStorageManager(),
            controllerFactory = FakeRecorderControllerFactory(FakeRecorderController()),
        )

        assertEquals(RecordingServiceCommandResult.Idle, coordinator.stop())
        assertEquals(RecordingServiceCommandResult.Idle, coordinator.stop())
    }

    @Test
    fun notificationSpec_usesStableChannelAndNotificationId() {
        val spec = SeminarRecordingNotificationSpecFactory().recording(
            seminarId = 12L,
            seminarTitle = "Domain Adaptation Seminar",
        )

        assertEquals(SeminarRecordingNotificationContract.CHANNEL_ID, spec.channelId)
        assertEquals(SeminarRecordingNotificationContract.NOTIFICATION_ID, spec.notificationId)
        assertTrue(spec.isOngoing)
        assertEquals("Domain Adaptation Seminar", spec.contentText)
    }
}

private fun coordinator(
    repository: FakeRecordingRepository,
    storage: FakeMediaStorageManager,
    controllerFactory: FakeRecorderControllerFactory,
): RecordingServiceCoordinator {
    return RecordingServiceCoordinator(
        recordingRepository = repository,
        mediaStorageManager = storage,
        recorderControllerFactory = controllerFactory,
        clockProvider = ClockProvider { Instant.parse("2026-08-13T08:00:00Z") },
    )
}

private class FakeRecordingRepository : RecordingRepository {
    var beginCalls = 0
    var recording = recordingSession()

    override fun observeLatestRecordingForSeminar(seminarId: Long): Flow<RecordingSession?> = flowOf(recording)

    override suspend fun beginRecordingForActiveSeminar(
        seminarId: Long,
        filePath: String,
        startedAt: Instant,
    ): BeginRecordingResult {
        beginCalls += 1
        recording = recording.copy(
            seminarId = seminarId,
            filePath = filePath,
            startedAt = startedAt,
            state = RecordingState.RECORDING,
            errorMessage = null,
        )
        return BeginRecordingResult.Started(recording, "Seminar $seminarId")
    }

    override suspend fun completeRecording(
        recordingId: Long,
        endedAt: Instant,
        durationMs: Long,
    ): CompleteRecordingResult {
        recording = recording.copy(
            endedAt = endedAt,
            durationMs = durationMs,
            state = RecordingState.COMPLETED,
            errorMessage = null,
        )
        return CompleteRecordingResult.Completed(recording)
    }

    override suspend fun failRecording(
        recordingId: Long,
        endedAt: Instant,
        errorMessage: String,
    ): FailRecordingResult {
        recording = recording.copy(
            endedAt = endedAt,
            state = RecordingState.FAILED,
            errorMessage = errorMessage,
        )
        return FailRecordingResult.Failed(recording)
    }

    override suspend fun failOpenRecordings(endedAt: Instant, errorMessage: String): Int = 0
}

private class FakeMediaStorageManager : MediaStorageManager {
    val createdForSeminarIds = mutableListOf<Long>()
    val createdRelativePaths = mutableListOf<String>()

    override suspend fun importAbstractPdf(seminarId: Long, sourceUri: String): StoredFile = error("Not used")

    override suspend fun createRecordingOutputFile(
        seminarId: Long,
        startedAt: Instant,
    ): RecordingOutputFile {
        createdForSeminarIds += seminarId
        val path = "seminars/$seminarId/recordings/recording.m4a"
        createdRelativePaths += path
        return RecordingOutputFile(
            displayName = "recording.m4a",
            relativePath = path,
            file = File("/tmp/seminararc-test/$path"),
        )
    }

    override suspend fun deleteRelativeFile(relativePath: String) = Unit

    override suspend fun deleteSeminarMedia(seminarId: Long) = Unit
}

private class FakeRecorderControllerFactory(
    private val controller: FakeRecorderController,
) : RecorderControllerFactory {
    override fun create(): RecorderController = controller
}

private class FakeRecorderController(
    private val startResult: RecorderStartResult = RecorderStartResult.Started,
    private val stopResult: RecorderStopResult = RecorderStopResult.Stopped(durationMs = 1_000L),
) : RecorderController {
    var startCalls = 0
    var stopCalls = 0
    var releaseCalls = 0

    override val isRecording: Boolean
        get() = startCalls > stopCalls

    override fun start(outputFile: File): RecorderStartResult {
        startCalls += 1
        return startResult
    }

    override fun stop(): RecorderStopResult {
        stopCalls += 1
        return stopResult
    }

    override fun release() {
        releaseCalls += 1
    }
}

private fun recordingSession(): RecordingSession {
    return RecordingSession(
        id = 1L,
        seminarId = 12L,
        filePath = "seminars/12/recordings/recording.m4a",
        startedAt = Instant.parse("2026-08-13T08:00:00Z"),
        endedAt = null,
        durationMs = null,
        state = RecordingState.RECORDING,
        errorMessage = null,
    )
}

