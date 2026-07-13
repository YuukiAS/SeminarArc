package com.yuukias.seminararc.data.local.dao

import androidx.room.Dao
import androidx.room.Query

@Dao
interface TimelineDao {
    @Query("SELECT COUNT(*) FROM timeline_events")
    suspend fun count(): Int
}
