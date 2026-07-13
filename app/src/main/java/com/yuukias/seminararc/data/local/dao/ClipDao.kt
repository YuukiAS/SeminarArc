package com.yuukias.seminararc.data.local.dao

import androidx.room.Dao
import androidx.room.Query

@Dao
interface ClipDao {
    @Query("SELECT COUNT(*) FROM audio_clips")
    suspend fun count(): Int
}
