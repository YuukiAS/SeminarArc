package com.yuukias.seminararc.recording

import com.yuukias.seminararc.domain.model.BeginRecordingResult
import com.yuukias.seminararc.domain.model.CompleteRecordingResult
import com.yuukias.seminararc.domain.model.CompleteSeminarResult
import com.yuukias.seminararc.domain.model.EndSeminarResult
import com.yuukias.seminararc.domain.model.FailRecordingResult
import com.yuukias.seminararc.domain.model.RecordingServiceState
import com.yuukias.seminararc.domain.model.RecordingSession
import com.yuukias.seminararc.domain.model.RecordingState
import com.yuukias.seminararc.domain.repository.RecordingRepository
import com.yuukias.seminararc.domain.repository.SeminarRepository
import com.yuukias.seminararc.domain.usecase.EndSeminarUseCase
import com.yuukias.seminararc.recording.service.RecordingRuntimeController
import com.yuukias.seminararc.recording.service.RecordingServiceCommandResult
import com.yuukias.seminararc.recording.service.RecordingServiceStarter
import com.yuukias.seminararc.util.ClockProvider
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EndSeminarUseCaseTest {

    @Test
    fun invoke_whenLiveRecorderStops_completesSeminarAfterStop() = runTest {
        val seminarRepository = EndFakeSeminarRepository(CompleteSeminarResult.Completed(12L))
        val runtime = EndFakeRuntimeController(
            hasLive = true,
            stopResult = RecordingServiceCommandResult.Stopped,
        )
        val serviceStarter = EndFakeServiceStarter()
        val useCase = useCase(seminarRepository = seminarRepository, runtime = runtime, serviceStarter = serviceStarter)

        val result = useCase(12L)

        assertEquals(EndSeminarResult.Completed(12L), result)
        assertEquals(listOf("stop", "complete"), runtime.events + seminarRepository.events)
        assertEquals(1, serviceStarter.stopCalls)
    }

    @Test
    fun invoke_whenStopFails_doesNotCompleteSeminar() = runTest {
        val seminarRepository = EndFakeSeminarRepository(CompleteSeminarResult.Completed(12L))
        val runtime = EndFakeRuntimeController(
            hasLive = true,
            stopResult = RecordingServiceCommandResult.Failed("finalize failed"),
        )
        val serviceStarter = EndFakeServiceStarter()
        val useCase = useCase(seminarRepository = seminarRepository, runtime = runtime, serviceStarter = serviceStarter)

        val result = useCase(12L)

        assertEquals(EndSeminarResult.StopFailed(12L, "finalize failed"), result)
        assertTrue(seminarRepository.events.isEmpty())
        assertEquals(1, serviceStarter.stopCalls)
    }

    @Test
    fun invoke_whenNoLiveRecorder_failsOnlyCurrentSeminarOpenRowsThenCompletes() = runTest {
        val recordingRepository = EndFakeRecordingRepository(openRecordingIds = listOf(7L))
        val seminarRepository = EndFakeSeminarRepository(CompleteSeminarResult.Completed(12L))
        val runtime = EndFakeRuntimeController(hasLive = false)
        val useCase = useCase(
            seminarRepository = seminarRepository,
            recordingRepository = recordingRepository,
            runtime = runtime,
        )

        val result = useCase(12L)

        assertEquals(EndSeminarResult.Completed(12L), result)
        assertEquals(listOf(12L), recordingRepository.openIdsRequestedForSeminar)
        assertEquals(listOf(listOf(7L)), recordingRepository.failedRecordingIdBatches)
    }

    @Test
    fun invoke_whenAlreadyCompleted_returnsExplicitResult() = runTest {
        val useCase = useCase(
            seminarRepository = EndFakeSeminarRepository(CompleteSeminarResult.AlreadyCompleted(12L)),
            runtime = EndFakeRuntimeController(hasLive = false),
        )

        val result = useCase(12L)

        assertEquals(EndSeminarResult.AlreadyCompleted(12L), result)
    }
}

private fun useCase(
    seminarRepository: EndFakeSeminarRepository,
    recordingRepository: EndFakeRecordingRepository = EndFakeRecordingRepository(),
    runtime: EndFakeRuntimeController,
    serviceStarter: EndFakeServiceStarter = EndFakeServiceStarter(),
): EndSeminarUseCase {
    return EndSeminarUseCase(
        seminarRepository = seminarRepository,
        recordingRepository = recordingRepository,
        runtimeController = runtime,
        serviceStarter = serviceStarter,
        clockProvider = ClockProvider { Instant.parse("2026-08-13T08:00:00Z") },
    )
}

private class EndFakeRuntimeController(
    private val hasLive: Boolean,
    private val stopResult: RecordingServiceCommandResult = RecordingServiceCommandResult.Idle,
) : RecordingRuntimeController {
    val events = mutableListOf<String>()
    override val state = MutableStateFlow<RecordingServiceState>(RecordingServiceState.Idle)

    override fun hasLiveRecordingForSeminar(seminarId: Long): Boolean = hasLive

    override suspend fun stopActiveRecording(): RecordingServiceCommandResult {
        events += "stop"
        return stopResult
    }

    override suspend fun abandonActiveRecording(errorMessage: String): RecordingServiceCommandResult {
        events += "abandon"
        return RecordingServiceCommandResult.Failed(errorMessage)
    }
}

private class EndFakeRecordingRepository(
    private val openRecordingIds: List<Long> = emptyList(),
) : RecordingRepository {
    val openIdsRequestedForSeminar = mutableListOf<Long>()
    val failedRecordingIdBatches = mutableListOf<List<Long>>()

    override fun observeLatestRecordingForSeminar(seminarId: Long): Flow<RecordingSession?> = flowOf(null)
    override suspend fun beginRecordingForActiveSeminar(seminarId: Long, filePath: String, startedAt: Instant): BeginRecordingResult = error("Not used")
    override suspend fun completeRecording(recordingId: Long, endedAt: Instant, durationMs: Long): CompleteRecordingResult = error("Not used")
    override suspend fun failRecording(recordingId: Long, endedAt: Instant, errorMessage: String): FailRecordingResult = error("Not used")
    override suspend fun getOpenRecordingIds(): List<Long> = emptyList()
    override suspend fun getOpenRecordingIdsForSeminar(seminarId: Long): List<Long> {
        openIdsRequestedForSeminar += seminarId
        return openRecordingIds
    }
    override suspend fun failRecordings(recordingIds: List<Long>, endedAt: Instant, errorMessage: String): Int {
        failedRecordingIdBatches += recordingIds
        return recordingIds.size
    }
    override suspend fun failOpenRecordings(endedAt: Instant, errorMessage: String): Int = 0
}

private class EndFakeServiceStarter : RecordingServiceStarter {
    var stopCalls = 0
    override fun start(seminarId: Long) = Unit
    override fun stop() {
        stopCalls += 1
    }
}

private class EndFakeSeminarRepository(
    private val completeResult: CompleteSeminarResult,
) : SeminarRepository by UnusedSeminarRepository {
    val events = mutableListOf<String>()
    override suspend fun completeActiveSeminar(seminarId: Long): CompleteSeminarResult {
        events += "complete"
        return completeResult
    }
}

private object UnusedSeminarRepository : SeminarRepository {
    override fun observeSeminars(filter: com.yuukias.seminararc.domain.model.SeminarListFilter, query: String) = flowOf(emptyList<com.yuukias.seminararc.domain.model.SeminarSummary>())
    override fun observeSeminarDetail(seminarId: Long) = flowOf<com.yuukias.seminararc.domain.model.SeminarDetail?>(null)
    override suspend fun getSeminarEditorData(seminarId: Long) = null
    override suspend fun saveSeminar(input: com.yuukias.seminararc.domain.model.SeminarDraftInput): Long = input.id ?: 1L
    override suspend fun getActiveSeminarSessionState() = com.yuukias.seminararc.domain.model.ActiveSeminarSessionState.None
    override suspend fun startSeminarSession(seminarId: Long) = error("Not used")
    override suspend fun completeActiveSeminar(seminarId: Long): CompleteSeminarResult = error("Not used")
    override suspend fun importAbstractPdf(seminarId: Long, sourceUri: String) = error("Not used")
    override suspend fun removeAbstractPdf(seminarId: Long) = Unit
    override suspend fun setFavorite(seminarId: Long, isFavorite: Boolean) = Unit
    override suspend fun setRating(seminarId: Long, rating: Int?) = Unit
    override suspend fun deleteSeminar(seminarId: Long) = Unit
}
