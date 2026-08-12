package com.yuukias.seminararc.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.yuukias.seminararc.data.local.entity.RecordingEntity
import com.yuukias.seminararc.domain.model.RecordingState
import java.time.Instant
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingDao {
    @Query("SELECT COUNT(*) FROM recordings")
    suspend fun count(): Int

    @Query("SELECT * FROM recordings WHERE id = :recordingId")
    suspend fun getRecording(recordingId: Long): RecordingEntity?

    @Query("SELECT * FROM recordings WHERE seminarId = :seminarId ORDER BY startedAt DESC LIMIT 1")
    suspend fun getLatestRecordingForSeminar(seminarId: Long): RecordingEntity?

    @Query("SELECT * FROM recordings WHERE seminarId = :seminarId ORDER BY startedAt DESC LIMIT 1")
    fun observeLatestRecordingForSeminar(seminarId: Long): Flow<RecordingEntity?>

    @Query(
        """
        SELECT * FROM recordings
        WHERE seminarId = :seminarId AND state = :state
        ORDER BY startedAt DESC
        LIMIT 1
        """
    )
    suspend fun getLatestRecordingByState(
        seminarId: Long,
        state: RecordingState,
    ): RecordingEntity?

    @Query("SELECT * FROM recordings WHERE state = :state ORDER BY startedAt DESC")
    suspend fun getRecordingsByState(state: RecordingState): List<RecordingEntity>

    @Insert
    suspend fun insertRecording(entity: RecordingEntity): Long

    @Query(
        """
        UPDATE recordings
        SET state = :completedState,
            endedAt = :endedAt,
            durationMs = :durationMs,
            errorMessage = NULL
        WHERE id = :recordingId AND state = :recordingState
        """
    )
    suspend fun markRecordingCompleted(
        recordingId: Long,
        recordingState: RecordingState,
        completedState: RecordingState,
        endedAt: Instant,
        durationMs: Long,
    ): Int

    @Query(
        """
        UPDATE recordings
        SET state = :failedState,
            endedAt = :endedAt,
            errorMessage = :errorMessage
        WHERE id = :recordingId
        """
    )
    suspend fun markRecordingFailed(
        recordingId: Long,
        failedState: RecordingState,
        endedAt: Instant,
        errorMessage: String,
    ): Int

    @Query(
        """
        UPDATE recordings
        SET state = :failedState,
            endedAt = :endedAt,
            errorMessage = :errorMessage
        WHERE state = :recordingState
        """
    )
    suspend fun markOpenRecordingsFailed(
        recordingState: RecordingState,
        failedState: RecordingState,
        endedAt: Instant,
        errorMessage: String,
    ): Int
}
