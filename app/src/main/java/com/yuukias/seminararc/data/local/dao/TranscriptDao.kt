package com.yuukias.seminararc.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.yuukias.seminararc.data.local.entity.SummaryDraftEntity
import com.yuukias.seminararc.data.local.entity.TranscriptEntity
import com.yuukias.seminararc.data.local.entity.TranscriptSegmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TranscriptDao {
    @Query("SELECT * FROM transcripts WHERE seminarId = :seminarId ORDER BY updatedAt DESC, id DESC")
    fun observeTranscripts(seminarId: Long): Flow<List<TranscriptEntity>>

    @Query("SELECT * FROM transcript_segments WHERE transcriptId = :transcriptId ORDER BY startOffsetMs ASC, id ASC")
    fun observeSegments(transcriptId: Long): Flow<List<TranscriptSegmentEntity>>

    @Query("SELECT * FROM summary_drafts WHERE seminarId = :seminarId ORDER BY updatedAt DESC, id DESC")
    fun observeSummaryDrafts(seminarId: Long): Flow<List<SummaryDraftEntity>>

    @Query("SELECT * FROM transcripts WHERE id = :transcriptId")
    suspend fun getTranscript(transcriptId: Long): TranscriptEntity?

    @Query("SELECT * FROM transcripts WHERE seminarId = :seminarId AND recordingId = :recordingId AND providerId = :providerId ORDER BY updatedAt DESC, id DESC LIMIT 1")
    suspend fun getLatestTranscriptForRecording(
        seminarId: Long,
        recordingId: Long,
        providerId: String,
    ): TranscriptEntity?

    @Query("SELECT * FROM transcript_segments WHERE transcriptId = :transcriptId ORDER BY startOffsetMs ASC, id ASC")
    suspend fun getSegments(transcriptId: Long): List<TranscriptSegmentEntity>

    @Query("SELECT * FROM summary_drafts WHERE id = :draftId")
    suspend fun getSummaryDraft(draftId: Long): SummaryDraftEntity?

    @Query("SELECT * FROM summary_drafts WHERE seminarId = :seminarId AND inputFingerprint = :inputFingerprint LIMIT 1")
    suspend fun getSummaryDraftByFingerprint(
        seminarId: Long,
        inputFingerprint: String,
    ): SummaryDraftEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTranscript(entity: TranscriptEntity): Long

    @Update
    suspend fun updateTranscript(entity: TranscriptEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSegments(entities: List<TranscriptSegmentEntity>): List<Long>

    @Query("DELETE FROM transcript_segments WHERE transcriptId = :transcriptId")
    suspend fun deleteSegments(transcriptId: Long): Int

    @Upsert
    suspend fun upsertSummaryDraft(entity: SummaryDraftEntity): Long

    @Transaction
    suspend fun replaceSegments(
        transcriptId: Long,
        segments: List<TranscriptSegmentEntity>,
    ): List<Long> {
        deleteSegments(transcriptId)
        if (segments.isEmpty()) return emptyList()
        return insertSegments(segments)
    }
}
