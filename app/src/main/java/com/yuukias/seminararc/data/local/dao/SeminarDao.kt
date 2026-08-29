package com.yuukias.seminararc.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.yuukias.seminararc.data.local.entity.SeminarEntity
import com.yuukias.seminararc.domain.model.SeminarStatus
import kotlinx.coroutines.flow.Flow
import java.time.Instant

data class SeminarListRow(
    val id: Long,
    val title: String,
    val speaker: String?,
    val scheduledAt: Instant?,
    val location: String?,
    val status: SeminarStatus,
    val isFavorite: Boolean,
    val rating: Int?,
    val photoCount: Int,
    val clipCount: Int,
)

data class SeminarDetailRow(
    val id: Long,
    val title: String,
    val speaker: String?,
    val affiliation: String?,
    val scheduledAt: Instant?,
    val location: String?,
    val abstractText: String?,
    val abstractPdfPath: String?,
    val status: SeminarStatus,
    val sessionStartedAt: Instant?,
    val sessionEndedAt: Instant?,
    val rating: Int?,
    val isFavorite: Boolean,
    val photoCount: Int,
    val clipCount: Int,
    val recordingDurationMs: Long?,
)

data class TimelinePreviewRow(
    val id: Long,
    val type: com.yuukias.seminararc.domain.model.TimelineEventType,
    val offsetMs: Long,
    val text: String?,
    val photoPath: String?,
)

@Dao
interface SeminarDao {
    @Query(
        """
        SELECT
            s.id,
            s.title,
            s.speaker,
            s.scheduledAt,
            s.location,
            s.status,
            s.isFavorite,
            s.rating,
            (SELECT COUNT(*) FROM timeline_events t WHERE t.seminarId = s.id AND t.type = 'PHOTO') AS photoCount,
            (SELECT COUNT(*) FROM audio_clips c WHERE c.seminarId = s.id) AS clipCount
        FROM seminars s
        WHERE (:statusFilter IS NULL OR s.status = :statusFilter)
            AND (:favoritesOnly = 0 OR s.isFavorite = 1)
            AND (
                :query = '' OR
                s.title LIKE '%' || :query || '%' OR
                COALESCE(s.speaker, '') LIKE '%' || :query || '%'
            )
        ORDER BY
            CASE WHEN s.scheduledAt IS NULL THEN 1 ELSE 0 END,
            s.scheduledAt DESC,
            s.createdAt DESC
        """
    )
    fun observeSeminarList(
        statusFilter: SeminarStatus?,
        favoritesOnly: Int,
        query: String,
    ): Flow<List<SeminarListRow>>

    @Query(
        """
        SELECT
            s.id,
            s.title,
            s.speaker,
            s.affiliation,
            s.scheduledAt,
            s.location,
            s.abstractText,
            s.abstractPdfPath,
            s.status,
            s.sessionStartedAt,
            s.sessionEndedAt,
            s.rating,
            s.isFavorite,
            (SELECT COUNT(*) FROM timeline_events t WHERE t.seminarId = s.id AND t.type = 'PHOTO') AS photoCount,
            (SELECT COUNT(*) FROM audio_clips c WHERE c.seminarId = s.id) AS clipCount,
            (SELECT r.durationMs FROM recordings r WHERE r.seminarId = s.id ORDER BY r.startedAt DESC LIMIT 1) AS recordingDurationMs
        FROM seminars s
        WHERE s.id = :seminarId
        """
    )
    fun observeSeminarDetail(seminarId: Long): Flow<SeminarDetailRow?>

    @Query(
        """
        SELECT
            t.id,
            t.type,
            t.offsetMs,
            t.text,
            t.photoPath
        FROM timeline_events t
        WHERE t.seminarId = :seminarId
        ORDER BY t.offsetMs ASC
        LIMIT :limit
        """
    )
    fun observeTimelinePreview(
        seminarId: Long,
        limit: Int,
    ): Flow<List<TimelinePreviewRow>>

    @Query("SELECT * FROM seminars WHERE id = :seminarId")
    suspend fun getSeminar(seminarId: Long): SeminarEntity?

    @Query("SELECT * FROM seminars WHERE status = :status ORDER BY sessionStartedAt DESC, updatedAt DESC")
    suspend fun getSeminarsByStatus(status: SeminarStatus): List<SeminarEntity>

    @Insert
    suspend fun insertSeminar(entity: SeminarEntity): Long

    @Update
    suspend fun updateSeminar(entity: SeminarEntity)

    @Query("UPDATE seminars SET isFavorite = :isFavorite, updatedAt = :updatedAt WHERE id = :seminarId")
    suspend fun updateFavorite(seminarId: Long, isFavorite: Boolean, updatedAt: Instant)

    @Query("UPDATE seminars SET rating = :rating, updatedAt = :updatedAt WHERE id = :seminarId")
    suspend fun updateRating(seminarId: Long, rating: Int?, updatedAt: Instant)

    @Query("UPDATE seminars SET abstractPdfPath = :path, updatedAt = :updatedAt WHERE id = :seminarId")
    suspend fun updateAbstractPath(seminarId: Long, path: String?, updatedAt: Instant)

    @Query(
        """
        UPDATE seminars
        SET
            status = :activeStatus,
            sessionStartedAt = :startedAt,
            sessionEndedAt = NULL,
            updatedAt = :updatedAt
        WHERE id = :seminarId AND status = :draftStatus
        """
    )
    suspend fun markDraftSeminarActive(
        seminarId: Long,
        draftStatus: SeminarStatus,
        activeStatus: SeminarStatus,
        startedAt: Instant,
        updatedAt: Instant,
    ): Int

    @Query(
        """
        UPDATE seminars
        SET
            status = :completedStatus,
            sessionEndedAt = :endedAt,
            updatedAt = :updatedAt
        WHERE id = :seminarId AND status = :activeStatus
        """
    )
    suspend fun markActiveSeminarCompleted(
        seminarId: Long,
        activeStatus: SeminarStatus,
        completedStatus: SeminarStatus,
        endedAt: Instant,
        updatedAt: Instant,
    ): Int

    @Query("DELETE FROM seminars WHERE id = :seminarId")
    suspend fun deleteSeminar(seminarId: Long)
}
