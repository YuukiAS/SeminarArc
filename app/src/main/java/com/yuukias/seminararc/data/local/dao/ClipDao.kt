package com.yuukias.seminararc.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.yuukias.seminararc.data.local.entity.AudioClipEntity
import com.yuukias.seminararc.domain.model.ClipState
import kotlinx.coroutines.flow.Flow

@Dao
interface ClipDao {
    @Query("SELECT COUNT(*) FROM audio_clips")
    suspend fun count(): Int

    @Query("SELECT * FROM audio_clips WHERE id = :clipId")
    suspend fun getClip(clipId: Long): AudioClipEntity?

    @Query("SELECT * FROM audio_clips WHERE sourceEventId = :eventId LIMIT 1")
    suspend fun getClipForEvent(eventId: Long): AudioClipEntity?

    @Query("SELECT * FROM audio_clips WHERE seminarId = :seminarId ORDER BY sourceEventId ASC, id ASC")
    fun observeClipsForSeminar(seminarId: Long): Flow<List<AudioClipEntity>>

    @Insert
    suspend fun insertClip(entity: AudioClipEntity): Long

    @Query(
        """
        UPDATE audio_clips
        SET state = :state,
            filePath = :filePath,
            errorMessage = :errorMessage,
            retryCount = retryCount + :retryIncrement
        WHERE id = :clipId
        """
    )
    suspend fun updateClipState(
        clipId: Long,
        state: ClipState,
        filePath: String?,
        errorMessage: String?,
        retryIncrement: Int,
    ): Int

    @Query("DELETE FROM audio_clips WHERE sourceEventId = :eventId")
    suspend fun deleteClipForEvent(eventId: Long): Int
}
