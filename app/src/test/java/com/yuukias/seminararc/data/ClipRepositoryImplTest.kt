package com.yuukias.seminararc.data

import com.yuukias.seminararc.data.local.dao.ClipDao
import com.yuukias.seminararc.data.local.dao.RecordingDao
import com.yuukias.seminararc.data.local.entity.AudioClipEntity
import com.yuukias.seminararc.data.local.entity.RecordingEntity
import com.yuukias.seminararc.data.repository.ClipRepositoryImpl
import com.yuukias.seminararc.data.storage.MediaStorageManager
import com.yuukias.seminararc.data.storage.PhotoOutputFile
import com.yuukias.seminararc.data.storage.RecordingOutputFile
import com.yuukias.seminararc.data.storage.StoredFile
import com.yuukias.seminararc.domain.model.ClipState
import com.yuukias.seminararc.domain.model.RecordingState
import com.yuukias.seminararc.domain.model.TimelineEvent
import com.yuukias.seminararc.domain.model.TimelineEventType
import com.yuukias.seminararc.domain.usecase.ClipIntervalCalculator
import java.io.File
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClipRepositoryImplTest {

    @Test
    fun createPendingClipForMark_usesDefaultIntervalAndRecordingDurationClamp() = runTest {
        val clipDao = FakeClipDao()
        val repository = repository(
            clipDao = clipDao,
            recordingDao = FakeRecordingDao(recording(durationMs = 120_000L)),
        )

        val clip = repository.createPendingClipForMark(mark(offsetMs = 90_000L, recordingId = 7L))!!

        assertEquals(30_000L, clip.startOffsetMs)
        assertEquals(120_000L, clip.endOffsetMs)
        assertEquals(ClipState.PENDING, clip.state)
        assertNull(clip.filePath)
    }

    @Test
    fun createPendingClipForMark_withoutRecordingId_returnsNull() = runTest {
        val repository = repository(
            clipDao = FakeClipDao(),
            recordingDao = FakeRecordingDao(recording(durationMs = 120_000L)),
        )

        assertNull(repository.createPendingClipForMark(mark(offsetMs = 90_000L, recordingId = null)))
    }

    @Test
    fun retryClip_setsPendingAndIncrementsRetryCount() = runTest {
        val clipDao = FakeClipDao()
        val repository = repository(clipDao, FakeRecordingDao(recording()))
        val clip = repository.createPendingClipForMark(mark(offsetMs = 90_000L, recordingId = 7L))!!
        repository.markFailed(clip.id, "missing source")

        val retried = repository.retryClip(clip.id)!!

        assertEquals(ClipState.PENDING, retried.state)
        assertEquals(1, retried.retryCount)
        assertNull(retried.errorMessage)
    }

    @Test
    fun deleteClipForEvent_deletesOnlyOwnedClipFile() = runTest {
        val storage = FakeClipStorage()
        val clipDao = FakeClipDao()
        val repository = repository(clipDao, FakeRecordingDao(recording()), storage)
        val clip = repository.createPendingClipForMark(mark(offsetMs = 90_000L, recordingId = 7L))!!
        repository.markReady(clip.id, "seminars/5/clips/clip-1.m4a")

        repository.deleteClipForEvent(99L)

        assertEquals(listOf("seminars/5/clips/clip-1.m4a"), storage.deleted)
        assertEquals(emptyList<AudioClipEntity>(), clipDao.entities.value)
    }

    private fun repository(
        clipDao: FakeClipDao,
        recordingDao: FakeRecordingDao,
        storage: FakeClipStorage = FakeClipStorage(),
    ): ClipRepositoryImpl {
        return ClipRepositoryImpl(
            clipDao = clipDao,
            recordingDao = recordingDao,
            mediaStorageManager = storage,
            intervalCalculator = ClipIntervalCalculator(),
        )
    }
}

private class FakeClipDao : ClipDao {
    val entities = MutableStateFlow<List<AudioClipEntity>>(emptyList())
    private var nextId = 1L

    override suspend fun count(): Int = entities.value.size
    override suspend fun getClip(clipId: Long): AudioClipEntity? = entities.value.firstOrNull { it.id == clipId }
    override suspend fun getClipForEvent(eventId: Long): AudioClipEntity? = entities.value.firstOrNull { it.sourceEventId == eventId }
    override fun observeClipsForSeminar(seminarId: Long): Flow<List<AudioClipEntity>> {
        return entities.map { clips -> clips.filter { it.seminarId == seminarId } }
    }

    override suspend fun insertClip(entity: AudioClipEntity): Long {
        val id = nextId++
        entities.value = entities.value + entity.copy(id = id)
        return id
    }

    override suspend fun updateClipState(
        clipId: Long,
        state: ClipState,
        filePath: String?,
        errorMessage: String?,
        retryIncrement: Int,
    ): Int {
        var changed = 0
        entities.value = entities.value.map { clip ->
            if (clip.id == clipId) {
                changed += 1
                clip.copy(
                    state = state,
                    filePath = filePath,
                    errorMessage = errorMessage,
                    retryCount = clip.retryCount + retryIncrement,
                )
            } else {
                clip
            }
        }
        return changed
    }

    override suspend fun deleteClipForEvent(eventId: Long): Int {
        val before = entities.value.size
        entities.value = entities.value.filterNot { clip -> clip.sourceEventId == eventId }
        return before - entities.value.size
    }
}

private class FakeRecordingDao(
    private val recording: RecordingEntity,
) : RecordingDao {
    override suspend fun count(): Int = 1
    override suspend fun getRecording(recordingId: Long): RecordingEntity? = recording.takeIf { it.id == recordingId }
    override suspend fun getLatestRecordingForSeminar(seminarId: Long): RecordingEntity? = recording
    override fun observeLatestRecordingForSeminar(seminarId: Long): Flow<RecordingEntity?> = kotlinx.coroutines.flow.flowOf(recording)
    override fun observeRecordingsForSeminar(seminarId: Long): Flow<List<RecordingEntity>> = kotlinx.coroutines.flow.flowOf(listOf(recording))
    override suspend fun getLatestRecordingByState(seminarId: Long, state: RecordingState): RecordingEntity? = recording.takeIf { it.state == state }
    override suspend fun getRecordingsByState(state: RecordingState): List<RecordingEntity> = listOfNotNull(recording.takeIf { it.state == state })
    override suspend fun getRecordingIdsByState(state: RecordingState): List<Long> = if (recording.state == state) listOf(recording.id) else emptyList()
    override suspend fun getRecordingIdsBySeminarAndState(seminarId: Long, state: RecordingState): List<Long> = if (recording.seminarId == seminarId && recording.state == state) listOf(recording.id) else emptyList()
    override suspend fun insertRecording(entity: RecordingEntity): Long = error("Not used")
    override suspend fun markRecordingCompleted(recordingId: Long, recordingState: RecordingState, completedState: RecordingState, endedAt: Instant, durationMs: Long): Int = error("Not used")
    override suspend fun markRecordingFailed(recordingId: Long, failedState: RecordingState, endedAt: Instant, errorMessage: String): Int = error("Not used")
    override suspend fun markRecordingsFailed(recordingIds: List<Long>, recordingState: RecordingState, failedState: RecordingState, endedAt: Instant, errorMessage: String): Int = error("Not used")
    override suspend fun markOpenRecordingsFailed(recordingState: RecordingState, failedState: RecordingState, endedAt: Instant, errorMessage: String): Int = error("Not used")
}

private class FakeClipStorage : MediaStorageManager {
    val deleted = mutableListOf<String>()
    override suspend fun importAbstractPdf(seminarId: Long, sourceUri: String): StoredFile = error("Not used")
    override suspend fun createRecordingOutputFile(seminarId: Long, startedAt: Instant): RecordingOutputFile = error("Not used")
    override suspend fun createPhotoOutputFile(seminarId: Long, capturedAt: Instant): PhotoOutputFile = error("Not used")
    override suspend fun createClipOutputFile(seminarId: Long, clipId: Long): com.yuukias.seminararc.data.storage.ClipOutputFile = error("Not used")
    override suspend fun resolveReadableRelativeFile(relativePath: String): File? = null
    override suspend fun deleteRelativeFile(relativePath: String) {
        deleted += relativePath
    }
    override suspend fun deleteSeminarMedia(seminarId: Long) = Unit
}

private fun mark(offsetMs: Long, recordingId: Long?): TimelineEvent {
    return TimelineEvent(
        id = 99L,
        seminarId = 5L,
        recordingId = recordingId,
        type = TimelineEventType.MARK,
        offsetMs = offsetMs,
        createdAt = Instant.parse("2026-08-29T08:00:00Z"),
        text = null,
        photoPath = null,
    )
}

private fun recording(durationMs: Long? = 180_000L): RecordingEntity {
    return RecordingEntity(
        id = 7L,
        seminarId = 5L,
        filePath = "seminars/5/recordings/recording.m4a",
        startedAt = Instant.parse("2026-08-29T08:00:00Z"),
        endedAt = Instant.parse("2026-08-29T08:03:00Z"),
        durationMs = durationMs,
        state = RecordingState.COMPLETED,
        errorMessage = null,
    )
}
