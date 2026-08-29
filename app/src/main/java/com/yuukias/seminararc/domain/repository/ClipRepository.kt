package com.yuukias.seminararc.domain.repository

import com.yuukias.seminararc.domain.model.AudioClip
import com.yuukias.seminararc.domain.model.RecordingSession
import com.yuukias.seminararc.domain.model.TimelineEvent
import kotlinx.coroutines.flow.Flow

interface ClipRepository {
    fun observeClipsForSeminar(seminarId: Long): Flow<List<AudioClip>>

    suspend fun createPendingClipForMark(mark: TimelineEvent): AudioClip?

    suspend fun getClip(clipId: Long): AudioClip?

    suspend fun getRecordingForClip(clip: AudioClip): RecordingSession?

    suspend fun markProcessing(clipId: Long): AudioClip?

    suspend fun markReady(clipId: Long, filePath: String): AudioClip?

    suspend fun markFailed(clipId: Long, message: String): AudioClip?

    suspend fun retryClip(clipId: Long): AudioClip?

    suspend fun deleteClipForEvent(eventId: Long)
}
