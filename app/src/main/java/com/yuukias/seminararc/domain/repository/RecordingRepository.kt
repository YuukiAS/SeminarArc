package com.yuukias.seminararc.domain.repository

import com.yuukias.seminararc.domain.model.BeginRecordingResult
import com.yuukias.seminararc.domain.model.CompleteRecordingResult
import com.yuukias.seminararc.domain.model.FailRecordingResult
import com.yuukias.seminararc.domain.model.RecordingSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant

interface RecordingRepository {
    fun observeLatestRecordingForSeminar(seminarId: Long): Flow<RecordingSession?>

    fun observeRecordingsForSeminar(seminarId: Long): Flow<List<RecordingSession>> {
        return observeLatestRecordingForSeminar(seminarId).map { recording ->
            if (recording == null) emptyList() else listOf(recording)
        }
    }

    suspend fun beginRecordingForActiveSeminar(
        seminarId: Long,
        filePath: String,
        startedAt: Instant,
    ): BeginRecordingResult

    suspend fun completeRecording(
        recordingId: Long,
        endedAt: Instant,
        durationMs: Long,
    ): CompleteRecordingResult

    suspend fun failRecording(
        recordingId: Long,
        endedAt: Instant,
        errorMessage: String,
    ): FailRecordingResult

    suspend fun getOpenRecordingIds(): List<Long>

    suspend fun getOpenRecordingIdsForSeminar(seminarId: Long): List<Long>

    suspend fun failRecordings(
        recordingIds: List<Long>,
        endedAt: Instant,
        errorMessage: String,
    ): Int

    suspend fun failOpenRecordings(
        endedAt: Instant,
        errorMessage: String,
    ): Int
}
