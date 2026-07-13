package com.yuukias.seminararc.data.local.dao

import androidx.room.Dao
import androidx.room.Query

@Dao
interface RecordingDao {
    @Query("SELECT COUNT(*) FROM recordings")
    suspend fun count(): Int
}
