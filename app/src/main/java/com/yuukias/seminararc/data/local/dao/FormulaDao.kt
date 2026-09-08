package com.yuukias.seminararc.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.yuukias.seminararc.data.local.entity.FormulaRegionEntity
import com.yuukias.seminararc.data.local.entity.FormulaResultEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FormulaDao {
    @Query("SELECT * FROM formula_regions WHERE seminarId = :seminarId ORDER BY createdAt ASC, id ASC")
    fun observeRegionsForSeminar(seminarId: Long): Flow<List<FormulaRegionEntity>>

    @Query("SELECT * FROM formula_regions WHERE id = :regionId")
    suspend fun getRegion(regionId: Long): FormulaRegionEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRegion(entity: FormulaRegionEntity): Long

    @Update
    suspend fun updateRegion(entity: FormulaRegionEntity)

    @Query("DELETE FROM formula_regions WHERE id = :regionId")
    suspend fun deleteRegion(regionId: Long): Int

    @Query("SELECT * FROM formula_results WHERE seminarId = :seminarId ORDER BY updatedAt DESC, id DESC")
    fun observeResultsForSeminar(seminarId: Long): Flow<List<FormulaResultEntity>>

    @Query("SELECT * FROM formula_results WHERE regionId = :regionId ORDER BY updatedAt DESC, id DESC")
    fun observeResultsForRegion(regionId: Long): Flow<List<FormulaResultEntity>>

    @Query("SELECT * FROM formula_results WHERE id = :resultId")
    suspend fun getResult(resultId: Long): FormulaResultEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertResult(entity: FormulaResultEntity): Long

    @Update
    suspend fun updateResult(entity: FormulaResultEntity)
}
