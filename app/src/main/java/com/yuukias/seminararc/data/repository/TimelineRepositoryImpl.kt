package com.yuukias.seminararc.data.repository

import com.yuukias.seminararc.data.local.dao.TimelineDao
import com.yuukias.seminararc.data.local.entity.TimelineEventEntity
import com.yuukias.seminararc.data.storage.MediaStorageManager
import com.yuukias.seminararc.domain.model.TimelineEvent
import com.yuukias.seminararc.domain.model.TimelineEventType
import com.yuukias.seminararc.domain.repository.DeleteTimelineEventResult
import com.yuukias.seminararc.domain.repository.TimelineRepository
import com.yuukias.seminararc.util.ClockProvider
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TimelineRepositoryImpl @Inject constructor(
    private val timelineDao: TimelineDao,
    private val mediaStorageManager: MediaStorageManager,
    private val clockProvider: ClockProvider,
) : TimelineRepository {

    override fun observeTimelineEvents(seminarId: Long): Flow<List<TimelineEvent>> {
        return timelineDao.observeEventsForSeminar(seminarId).map { events ->
            events.map { entity -> entity.toDomain() }
        }
    }

    override suspend fun addMark(seminarId: Long, recordingId: Long?, offsetMs: Long): TimelineEvent {
        return insert(
            seminarId = seminarId,
            recordingId = recordingId,
            type = TimelineEventType.MARK,
            offsetMs = offsetMs,
            text = null,
            photoPath = null,
        )
    }

    override suspend fun addNote(
        seminarId: Long,
        recordingId: Long?,
        offsetMs: Long,
        text: String,
    ): TimelineEvent {
        return insert(
            seminarId = seminarId,
            recordingId = recordingId,
            type = TimelineEventType.NOTE,
            offsetMs = offsetMs,
            text = text.trim().take(MAX_TEXT_LENGTH),
            photoPath = null,
        )
    }

    override suspend fun addQuestion(
        seminarId: Long,
        recordingId: Long?,
        offsetMs: Long,
        text: String,
    ): TimelineEvent {
        return insert(
            seminarId = seminarId,
            recordingId = recordingId,
            type = TimelineEventType.QUESTION,
            offsetMs = offsetMs,
            text = text.trim().take(MAX_TEXT_LENGTH),
            photoPath = null,
        )
    }

    override suspend fun addPhoto(
        seminarId: Long,
        recordingId: Long?,
        offsetMs: Long,
        photoPath: String,
    ): TimelineEvent {
        return insert(
            seminarId = seminarId,
            recordingId = recordingId,
            type = TimelineEventType.PHOTO,
            offsetMs = offsetMs,
            text = null,
            photoPath = photoPath,
        )
    }

    override suspend fun deleteEvent(eventId: Long): DeleteTimelineEventResult {
        val existing = timelineDao.getEvent(eventId) ?: return DeleteTimelineEventResult.NotFound(eventId)
        val deletedRows = timelineDao.deleteEvent(eventId)
        if (deletedRows != 1) {
            return DeleteTimelineEventResult.NotFound(eventId)
        }
        if (existing.type == TimelineEventType.PHOTO && existing.photoPath != null) {
            mediaStorageManager.deleteRelativeFile(existing.photoPath)
            return DeleteTimelineEventResult.Deleted(eventId, existing.photoPath)
        }
        return DeleteTimelineEventResult.Deleted(eventId, null)
    }

    private suspend fun insert(
        seminarId: Long,
        recordingId: Long?,
        type: TimelineEventType,
        offsetMs: Long,
        text: String?,
        photoPath: String?,
    ): TimelineEvent {
        val id = timelineDao.insertEvent(
            TimelineEventEntity(
                seminarId = seminarId,
                recordingId = recordingId,
                type = type,
                offsetMs = offsetMs.coerceAtLeast(0L),
                createdAt = clockProvider.now(),
                text = text?.takeIf { value -> value.isNotBlank() },
                photoPath = photoPath?.takeIf { value -> value.isNotBlank() },
            ),
        )
        return timelineDao.getEvent(id)?.toDomain()
            ?: error("Timeline event $id was not readable after insert.")
    }

    private fun TimelineEventEntity.toDomain(): TimelineEvent {
        return TimelineEvent(
            id = id,
            seminarId = seminarId,
            recordingId = recordingId,
            type = type,
            offsetMs = offsetMs,
            createdAt = createdAt,
            text = text,
            photoPath = photoPath,
        )
    }

    private companion object {
        const val MAX_TEXT_LENGTH = 4_000
    }
}
