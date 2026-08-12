package com.yuukias.seminararc.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import com.yuukias.seminararc.data.local.entity.RecordingEntity
import com.yuukias.seminararc.domain.model.RecordingState

@Dao
interface RecordingDao {
    @Query("SELECT COUNT(*) FROM recordings")
    suspend fun count(): Int

    @Query("SELECT * FROM recordings WHERE seminarId = :seminarId ORDER BY startedAt DESC LIMIT 1")
    suspend fun getLatestRecordingForSeminar(seminarId: Long): RecordingEntity?

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
}
