package com.yuukias.seminararc.data.repository

import com.yuukias.seminararc.data.local.dao.ClipDao
import com.yuukias.seminararc.data.local.dao.RecordingDao
import com.yuukias.seminararc.data.local.entity.AudioClipEntity
import com.yuukias.seminararc.data.local.entity.RecordingEntity
import com.yuukias.seminararc.data.storage.MediaStorageManager
import com.yuukias.seminararc.domain.model.AudioClip
import com.yuukias.seminararc.domain.model.ClipState
import com.yuukias.seminararc.domain.model.RecordingSession
import com.yuukias.seminararc.domain.model.TimelineEvent
import com.yuukias.seminararc.domain.repository.ClipRepository
import com.yuukias.seminararc.domain.usecase.ClipIntervalCalculator
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ClipRepositoryImpl @Inject constructor(
    private val clipDao: ClipDao,
    private val recordingDao: RecordingDao,
    private val mediaStorageManager: MediaStorageManager,
    private val intervalCalculator: ClipIntervalCalculator,
) : ClipRepository {

    override fun observeClipsForSeminar(seminarId: Long): Flow<List<AudioClip>> {
        return clipDao.observeClipsForSeminar(seminarId).map { clips -> clips.map { it.toDomain() } }
    }

    override suspend fun createPendingClipForMark(mark: TimelineEvent): AudioClip? {
        val recordingId = mark.recordingId ?: return null
        clipDao.getClipForEvent(mark.id)?.let { return it.toDomain() }
        val recording = recordingDao.getRecording(recordingId) ?: return null
        val interval = intervalCalculator.calculate(mark.offsetMs, recording.durationMs)
        val id = clipDao.insertClip(
            AudioClipEntity(
                seminarId = mark.seminarId,
                recordingId = recordingId,
                sourceEventId = mark.id,
                startOffsetMs = interval.startOffsetMs,
                endOffsetMs = interval.endOffsetMs,
                filePath = null,
                state = ClipState.PENDING,
                errorMessage = null,
            ),
        )
        return clipDao.getClip(id)?.toDomain()
    }

    override suspend fun getClip(clipId: Long): AudioClip? = clipDao.getClip(clipId)?.toDomain()

    override suspend fun getRecordingForClip(clip: AudioClip): RecordingSession? {
        return recordingDao.getRecording(clip.recordingId)?.toDomain()
    }

    override suspend fun markProcessing(clipId: Long): AudioClip? {
        clipDao.updateClipState(clipId, ClipState.PROCESSING, null, null, retryIncrement = 0)
        return getClip(clipId)
    }

    override suspend fun markReady(clipId: Long, filePath: String): AudioClip? {
        clipDao.updateClipState(clipId, ClipState.READY, filePath, null, retryIncrement = 0)
        return getClip(clipId)
    }

    override suspend fun markFailed(clipId: Long, message: String): AudioClip? {
        clipDao.updateClipState(clipId, ClipState.FAILED, null, message.take(MAX_ERROR_LENGTH), retryIncrement = 0)
        return getClip(clipId)
    }

    override suspend fun retryClip(clipId: Long): AudioClip? {
        clipDao.updateClipState(clipId, ClipState.PENDING, null, null, retryIncrement = 1)
        return getClip(clipId)
    }

    override suspend fun deleteClipForEvent(eventId: Long) {
        val existing = clipDao.getClipForEvent(eventId)
        if (existing?.filePath != null) {
            mediaStorageManager.deleteRelativeFile(existing.filePath)
        }
        clipDao.deleteClipForEvent(eventId)
    }

    private fun AudioClipEntity.toDomain(): AudioClip {
        return AudioClip(
            id = id,
            seminarId = seminarId,
            recordingId = recordingId,
            sourceEventId = sourceEventId,
            startOffsetMs = startOffsetMs,
            endOffsetMs = endOffsetMs,
            filePath = filePath,
            state = state,
            errorMessage = errorMessage,
            retryCount = retryCount,
        )
    }

    private fun RecordingEntity.toDomain(): RecordingSession {
        return RecordingSession(id, seminarId, filePath, startedAt, endedAt, durationMs, state, errorMessage)
    }

    private companion object {
        const val MAX_ERROR_LENGTH = 500
    }
}
