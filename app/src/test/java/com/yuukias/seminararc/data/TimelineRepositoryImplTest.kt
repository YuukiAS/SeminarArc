package com.yuukias.seminararc.data

import com.yuukias.seminararc.data.local.dao.TimelineDao
import com.yuukias.seminararc.data.local.entity.TimelineEventEntity
import com.yuukias.seminararc.data.repository.TimelineRepositoryImpl
import com.yuukias.seminararc.data.storage.MediaStorageManager
import com.yuukias.seminararc.data.storage.PhotoOutputFile
import com.yuukias.seminararc.data.storage.RecordingOutputFile
import com.yuukias.seminararc.data.storage.StoredFile
import com.yuukias.seminararc.domain.model.TimelineEventType
import com.yuukias.seminararc.domain.repository.ClipRepository
import com.yuukias.seminararc.domain.repository.DeleteTimelineEventResult
import com.yuukias.seminararc.util.ClockProvider
import java.io.File
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineRepositoryImplTest {

    private val now = Instant.parse("2026-08-29T08:00:00Z")

    @Test
    fun observeTimelineEvents_mapsEventsInRecoverableOffsetOrder() = runTest {
        val dao = FakeTimelineDao()
        val repository = repository(dao)

        repository.addNote(3L, recordingId = 8L, offsetMs = 3_000L, text = " later ")
        repository.addMark(3L, recordingId = 8L, offsetMs = 1_000L)

        val events = repository.observeTimelineEvents(3L).first()

        assertEquals(listOf(TimelineEventType.MARK, TimelineEventType.NOTE), events.map { it.type })
        assertEquals(listOf(1_000L, 3_000L), events.map { it.offsetMs })
        assertEquals("later", events.last().text)
    }

    @Test
    fun addQuestion_trimsAndCapsText() = runTest {
        val dao = FakeTimelineDao()
        val repository = repository(dao)
        val longText = " question ".repeat(600)

        val event = repository.addQuestion(4L, recordingId = null, offsetMs = -5L, text = longText)

        assertEquals(TimelineEventType.QUESTION, event.type)
        assertEquals(0L, event.offsetMs)
        assertEquals(4_000, event.text?.length)
        assertTrue(event.text!!.startsWith("question"))
    }

    @Test
    fun deletePhotoEvent_deletesRowAndOwnedPhotoPath() = runTest {
        val dao = FakeTimelineDao()
        val storage = FakeTimelineStorage()
        val repository = repository(dao, storage)
        val photo = repository.addPhoto(5L, recordingId = 9L, offsetMs = 2_000L, photoPath = "seminars/5/photos/photo.jpg")

        val result = repository.deleteEvent(photo.id)

        assertEquals(DeleteTimelineEventResult.Deleted(photo.id, "seminars/5/photos/photo.jpg"), result)
        assertEquals(listOf("seminars/5/photos/photo.jpg"), storage.deleted)
        assertEquals(emptyList<TimelineEventEntity>(), dao.entities.value)
    }

    private fun repository(
        dao: FakeTimelineDao,
        storage: FakeTimelineStorage = FakeTimelineStorage(),
    ): TimelineRepositoryImpl {
        return TimelineRepositoryImpl(
            timelineDao = dao,
            mediaStorageManager = storage,
            clockProvider = ClockProvider { now },
            clipRepository = FakeClipRepository(),
        )
    }
}

private class FakeTimelineDao : TimelineDao {
    val entities = MutableStateFlow<List<TimelineEventEntity>>(emptyList())
    private var nextId = 1L

    override suspend fun count(): Int = entities.value.size

    override suspend fun getEvent(eventId: Long): TimelineEventEntity? {
        return entities.value.firstOrNull { event -> event.id == eventId }
    }

    override fun observeEventsForSeminar(seminarId: Long): Flow<List<TimelineEventEntity>> {
        return entities.map { current ->
            current
                .filter { event -> event.seminarId == seminarId }
                .sortedWith(compareBy<TimelineEventEntity> { it.offsetMs }.thenBy { it.createdAt }.thenBy { it.id })
        }
    }

    override suspend fun insertEvent(entity: TimelineEventEntity): Long {
        val id = nextId++
        entities.value = entities.value + entity.copy(id = id)
        return id
    }

    override suspend fun deleteEvent(eventId: Long): Int {
        val before = entities.value.size
        entities.value = entities.value.filterNot { event -> event.id == eventId }
        return before - entities.value.size
    }
}

private class FakeTimelineStorage : MediaStorageManager {
    val deleted = mutableListOf<String>()

    override suspend fun importAbstractPdf(seminarId: Long, sourceUri: String): StoredFile = error("Not used")

    override suspend fun createRecordingOutputFile(seminarId: Long, startedAt: Instant): RecordingOutputFile = error("Not used")

    override suspend fun createPhotoOutputFile(seminarId: Long, capturedAt: Instant): PhotoOutputFile {
        val path = "seminars/$seminarId/photos/photo.jpg"
        return PhotoOutputFile(
            displayName = "photo.jpg",
            relativePath = path,
            file = File("/tmp/seminararc-test/$path"),
        )
    }

    override suspend fun createClipOutputFile(
        seminarId: Long,
        clipId: Long,
    ): com.yuukias.seminararc.data.storage.ClipOutputFile {
        val path = "seminars/$seminarId/clips/clip-$clipId.m4a"
        return com.yuukias.seminararc.data.storage.ClipOutputFile(
            displayName = "clip-$clipId.m4a",
            relativePath = path,
            file = File("/tmp/seminararc-test/$path"),
        )
    }

    override suspend fun resolveReadableRelativeFile(relativePath: String): File? = null

    override suspend fun deleteRelativeFile(relativePath: String) {
        deleted += relativePath
    }

    override suspend fun deleteSeminarMedia(seminarId: Long) = Unit
}

private class FakeClipRepository : ClipRepository {
    override fun observeClipsForSeminar(seminarId: Long): Flow<List<com.yuukias.seminararc.domain.model.AudioClip>> = kotlinx.coroutines.flow.flowOf(emptyList())
    override suspend fun createPendingClipForMark(mark: com.yuukias.seminararc.domain.model.TimelineEvent): com.yuukias.seminararc.domain.model.AudioClip? = null
    override suspend fun getClip(clipId: Long): com.yuukias.seminararc.domain.model.AudioClip? = null
    override suspend fun getRecordingForClip(clip: com.yuukias.seminararc.domain.model.AudioClip): com.yuukias.seminararc.domain.model.RecordingSession? = null
    override suspend fun markProcessing(clipId: Long): com.yuukias.seminararc.domain.model.AudioClip? = null
    override suspend fun markReady(clipId: Long, filePath: String): com.yuukias.seminararc.domain.model.AudioClip? = null
    override suspend fun markFailed(clipId: Long, message: String): com.yuukias.seminararc.domain.model.AudioClip? = null
    override suspend fun retryClip(clipId: Long): com.yuukias.seminararc.domain.model.AudioClip? = null
    override suspend fun deleteClipForEvent(eventId: Long) = Unit
}
