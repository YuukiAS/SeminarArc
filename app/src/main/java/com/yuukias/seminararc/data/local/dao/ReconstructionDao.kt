package com.yuukias.seminararc.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.yuukias.seminararc.data.local.entity.AssetTagEntity
import com.yuukias.seminararc.data.local.entity.OcrResultEntity
import com.yuukias.seminararc.data.local.entity.ProcessingJobEntity
import com.yuukias.seminararc.data.local.entity.SeminarAssetEntity
import com.yuukias.seminararc.data.local.entity.TagEntity
import com.yuukias.seminararc.domain.model.ProcessingJobState
import com.yuukias.seminararc.domain.model.ProcessingJobType
import com.yuukias.seminararc.domain.model.SeminarAssetType
import kotlinx.coroutines.flow.Flow

@Dao
interface ReconstructionDao {
    @Query("SELECT * FROM seminar_assets WHERE seminarId = :seminarId ORDER BY createdAt ASC, id ASC")
    fun observeAssetsForSeminar(seminarId: Long): Flow<List<SeminarAssetEntity>>

    @Query(
        """
        SELECT * FROM seminar_assets
        WHERE seminarId = :seminarId
            AND type IN (:types)
        ORDER BY createdAt ASC, id ASC
        """
    )
    fun observeAssetsForSeminarByTypes(
        seminarId: Long,
        types: List<SeminarAssetType>,
    ): Flow<List<SeminarAssetEntity>>

    @Query("SELECT * FROM seminar_assets WHERE id = :assetId")
    suspend fun getAsset(assetId: Long): SeminarAssetEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAsset(entity: SeminarAssetEntity): Long

    @Query("SELECT * FROM processing_jobs WHERE seminarId = :seminarId ORDER BY createdAt DESC, id DESC")
    fun observeJobsForSeminar(seminarId: Long): Flow<List<ProcessingJobEntity>>

    @Query(
        """
        SELECT * FROM processing_jobs
        WHERE inputAssetId = :inputAssetId
            AND type = :type
            AND state IN ('QUEUED', 'RUNNING')
        ORDER BY createdAt DESC, id DESC
        LIMIT 1
        """
    )
    suspend fun getActiveJobForInput(
        inputAssetId: Long,
        type: ProcessingJobType,
    ): ProcessingJobEntity?

    @Query("SELECT * FROM processing_jobs WHERE id = :jobId")
    suspend fun getJob(jobId: Long): ProcessingJobEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertJob(entity: ProcessingJobEntity): Long

    @Update
    suspend fun updateJob(entity: ProcessingJobEntity)

    @Query("SELECT * FROM ocr_results WHERE seminarId = :seminarId ORDER BY updatedAt DESC, id DESC")
    fun observeOcrResultsForSeminar(seminarId: Long): Flow<List<OcrResultEntity>>

    @Query("SELECT * FROM ocr_results WHERE assetId = :assetId")
    suspend fun getOcrResultForAsset(assetId: Long): OcrResultEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOcrResult(entity: OcrResultEntity): Long

    @Query(
        """
        SELECT tags.* FROM tags
        INNER JOIN asset_tags ON asset_tags.tagId = tags.id
        WHERE asset_tags.assetId = :assetId
        ORDER BY tags.isSystem DESC, tags.label ASC
        """
    )
    fun observeTagsForAsset(assetId: Long): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags WHERE key = :key LIMIT 1")
    suspend fun getSystemTag(key: String): TagEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(entity: TagEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAssetTag(entity: AssetTagEntity)

    @Query("DELETE FROM asset_tags WHERE assetId = :assetId AND tagId = :tagId")
    suspend fun deleteAssetTag(assetId: Long, tagId: Long): Int

    @Transaction
    suspend fun ensureSystemTag(entity: TagEntity): TagEntity {
        val insertedId = insertTag(entity)
        return if (insertedId > 0L) {
            entity.copy(id = insertedId)
        } else {
            getSystemTag(entity.key) ?: error("System tag ${entity.key} was not readable after insert.")
        }
    }
}
