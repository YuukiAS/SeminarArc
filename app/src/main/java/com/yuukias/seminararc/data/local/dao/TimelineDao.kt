package com.yuukias.seminararc.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.yuukias.seminararc.data.local.entity.TimelineEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TimelineDao {
    @Query("SELECT COUNT(*) FROM timeline_events")
    suspend fun count(): Int

    @Query("SELECT * FROM timeline_events WHERE id = :eventId")
    suspend fun getEvent(eventId: Long): TimelineEventEntity?

    @Query("SELECT * FROM timeline_events WHERE seminarId = :seminarId ORDER BY offsetMs ASC, createdAt ASC, id ASC")
    fun observeEventsForSeminar(seminarId: Long): Flow<List<TimelineEventEntity>>

    @Insert
    suspend fun insertEvent(entity: TimelineEventEntity): Long

    @Query("DELETE FROM timeline_events WHERE id = :eventId")
    suspend fun deleteEvent(eventId: Long): Int
}
