package com.yuukias.seminararc.domain.repository

import com.yuukias.seminararc.domain.model.TimelineEvent
import kotlinx.coroutines.flow.Flow

interface TimelineRepository {
    fun observeTimelineEvents(seminarId: Long): Flow<List<TimelineEvent>>

    suspend fun addMark(seminarId: Long, recordingId: Long?, offsetMs: Long): TimelineEvent

    suspend fun addNote(seminarId: Long, recordingId: Long?, offsetMs: Long, text: String): TimelineEvent

    suspend fun addQuestion(seminarId: Long, recordingId: Long?, offsetMs: Long, text: String): TimelineEvent

    suspend fun addPhoto(seminarId: Long, recordingId: Long?, offsetMs: Long, photoPath: String): TimelineEvent

    suspend fun deleteEvent(eventId: Long): DeleteTimelineEventResult
}

sealed interface DeleteTimelineEventResult {
    data class Deleted(val eventId: Long, val deletedPhotoPath: String?) : DeleteTimelineEventResult
    data class NotFound(val eventId: Long) : DeleteTimelineEventResult
}
