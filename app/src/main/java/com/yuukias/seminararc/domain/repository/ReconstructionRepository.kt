package com.yuukias.seminararc.domain.repository

import com.yuukias.seminararc.domain.model.AssetTag
import com.yuukias.seminararc.domain.model.OcrResult
import com.yuukias.seminararc.domain.model.ProcessingJob
import com.yuukias.seminararc.domain.model.ProcessingJobState
import com.yuukias.seminararc.domain.model.ProcessingJobType
import com.yuukias.seminararc.domain.model.SeminarAsset
import com.yuukias.seminararc.domain.model.SeminarAssetType
import com.yuukias.seminararc.domain.model.SeminarSystemTag
import kotlinx.coroutines.flow.Flow

interface ReconstructionRepository {
    fun observeAssetsForSeminar(seminarId: Long): Flow<List<SeminarAsset>>

    fun observePhotoAssetsForSeminar(seminarId: Long): Flow<List<SeminarAsset>>

    fun observeJobsForSeminar(seminarId: Long): Flow<List<ProcessingJob>>

    fun observeOcrResultsForSeminar(seminarId: Long): Flow<List<OcrResult>>

    fun observeTagsForAsset(assetId: Long): Flow<List<AssetTag>>

    fun observeAssetIdsForSystemTag(seminarId: Long, tag: SeminarSystemTag): Flow<List<Long>>

    suspend fun getAsset(assetId: Long): SeminarAsset?

    suspend fun getAssetByRelativePath(relativePath: String): SeminarAsset?

    suspend fun getJob(jobId: Long): ProcessingJob?

    suspend fun recoverInterruptedJobs(): List<ProcessingJob>

    suspend fun createDerivedAsset(input: CreateDerivedAssetInput): SeminarAsset

    suspend fun enqueueJob(input: EnqueueProcessingJobInput): ProcessingJob

    suspend fun requeueJob(jobId: Long): ProcessingJob?

    suspend fun markJobRunning(jobId: Long)

    suspend fun markJobSucceeded(jobId: Long, outputAssetId: Long?)

    suspend fun markJobFailed(jobId: Long, message: String, isRetryable: Boolean)

    suspend fun markJobCancelled(jobId: Long)

    suspend fun saveOcrResult(input: SaveOcrResultInput): OcrResult

    suspend fun editOcrResult(assetId: Long, editedText: String): Boolean

    suspend fun setSystemTag(assetId: Long, tag: SeminarSystemTag, enabled: Boolean)
}

data class CreateDerivedAssetInput(
    val seminarId: Long,
    val type: SeminarAssetType,
    val originAssetId: Long,
    val relativePath: String,
    val mimeType: String?,
    val displayName: String?,
)

data class EnqueueProcessingJobInput(
    val seminarId: Long,
    val type: ProcessingJobType,
    val inputAssetId: Long,
    val providerId: String,
    val providerVersion: String,
)

data class SaveOcrResultInput(
    val seminarId: Long,
    val assetId: Long,
    val recognizedText: String,
    val editedText: String?,
    val blockJson: String?,
    val languageHint: String?,
    val confidence: Float?,
    val providerId: String,
    val providerVersion: String,
)

fun ProcessingJob.isTerminal(): Boolean {
    return state == ProcessingJobState.SUCCEEDED ||
        state == ProcessingJobState.FAILED ||
        state == ProcessingJobState.CANCELLED
}
