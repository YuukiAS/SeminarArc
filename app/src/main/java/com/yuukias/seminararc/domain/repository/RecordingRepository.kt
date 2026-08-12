package com.yuukias.seminararc.domain.repository

import com.yuukias.seminararc.domain.model.BeginRecordingResult
import com.yuukias.seminararc.domain.model.CompleteRecordingResult
import com.yuukias.seminararc.domain.model.FailRecordingResult
import com.yuukias.seminararc.domain.model.RecordingSession
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface RecordingRepository {
    fun observeLatestRecordingForSeminar(seminarId: Long): Flow<RecordingSession?>

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

    suspend fun failOpenRecordings(
        endedAt: Instant,
        errorMessage: String,
    ): Int
}
