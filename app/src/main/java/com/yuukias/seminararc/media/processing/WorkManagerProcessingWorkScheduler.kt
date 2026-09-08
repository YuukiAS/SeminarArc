package com.yuukias.seminararc.media.processing

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.yuukias.seminararc.domain.image.ImageEnhancementOptions
import com.yuukias.seminararc.domain.model.ProcessingJob
import com.yuukias.seminararc.domain.model.ProcessingJobType
import com.yuukias.seminararc.domain.model.RecordingState
import com.yuukias.seminararc.domain.model.TranscriptLanguageHint
import com.yuukias.seminararc.domain.ocr.TextOcrLanguageMode
import com.yuukias.seminararc.domain.repository.EnqueueProcessingJobInput
import com.yuukias.seminararc.domain.repository.RecordingRepository
import com.yuukias.seminararc.domain.repository.ReconstructionRepository
import com.yuukias.seminararc.domain.repository.TranscriptRepository
import com.yuukias.seminararc.domain.image.ImageEnhancementProvider
import com.yuukias.seminararc.domain.ocr.TextOcrProvider
import com.yuukias.seminararc.domain.summary.SummaryProvider
import com.yuukias.seminararc.domain.transcription.TranscriptionProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class WorkManagerProcessingWorkScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val recordingRepository: RecordingRepository,
    private val reconstructionRepository: ReconstructionRepository,
    private val transcriptRepository: TranscriptRepository,
    private val imageEnhancementProvider: ImageEnhancementProvider,
    private val textOcrProvider: TextOcrProvider,
    private val transcriptionProvider: TranscriptionProvider,
    private val summaryProvider: SummaryProvider,
) : ProcessingWorkScheduler {
    private val workManager: WorkManager
        get() = WorkManager.getInstance(context)
    private val recoveryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override suspend fun enqueueImageEnhancement(
        assetId: Long,
        options: ImageEnhancementOptions,
    ): ProcessingJob? {
        val asset = reconstructionRepository.getAsset(assetId) ?: return null
        val job = reconstructionRepository.enqueueJob(
            EnqueueProcessingJobInput(
                seminarId = asset.seminarId,
                type = ProcessingJobType.IMAGE_ENHANCEMENT,
                inputAssetId = asset.id,
                providerId = imageEnhancementProvider.providerId,
                providerVersion = imageEnhancementProvider.providerVersion,
            ),
        )
        enqueue(job, imageEnhancementRequest(job.id, options), ExistingWorkPolicy.KEEP)
        return job
    }

    override suspend fun enqueueTextOcr(
        assetId: Long,
        languageMode: TextOcrLanguageMode,
    ): ProcessingJob? {
        val asset = reconstructionRepository.getAsset(assetId) ?: return null
        val job = reconstructionRepository.enqueueJob(
            EnqueueProcessingJobInput(
                seminarId = asset.seminarId,
                type = ProcessingJobType.TEXT_OCR,
                inputAssetId = asset.id,
                providerId = textOcrProvider.providerId,
                providerVersion = textOcrProvider.providerVersion,
            ),
        )
        enqueue(job, textOcrRequest(job.id, languageMode), ExistingWorkPolicy.KEEP)
        return job
    }

    override suspend fun enqueueTranscription(
        recordingId: Long,
        languageHint: TranscriptLanguageHint,
    ): ProcessingJob? {
        val recording = recordingRepository.getRecording(recordingId) ?: return null
        if (recording.state != RecordingState.COMPLETED) return null
        val sourceAsset = reconstructionRepository.getAssetByRelativePath(recording.filePath) ?: return null
        val job = reconstructionRepository.enqueueJob(
            EnqueueProcessingJobInput(
                seminarId = recording.seminarId,
                type = ProcessingJobType.TRANSCRIPTION,
                inputAssetId = sourceAsset.id,
                providerId = transcriptionProvider.providerId,
                providerVersion = transcriptionProvider.providerVersion,
            ),
        )
        enqueue(job, transcriptionRequest(job.id, recording.id, languageHint), ExistingWorkPolicy.KEEP)
        return job
    }

    override suspend fun enqueueSummaryDraft(
        seminarId: Long,
        transcriptId: Long,
        selectedSegmentIds: List<Long>,
        userNotes: String,
    ): ProcessingJob? {
        val transcript = transcriptRepository.getTranscript(transcriptId)
            ?.takeIf { it.seminarId == seminarId }
            ?: return null
        val inputAssetId = transcript.sourceAssetId ?: return null
        val payload = SummaryDraftWorkPayload(
            seminarId = seminarId,
            transcriptId = transcriptId,
            selectedSegmentIds = selectedSegmentIds.distinct(),
            userNotes = userNotes,
        )
        val payloadJson = json.encodeToString(payload)
        val job = reconstructionRepository.enqueueJob(
            EnqueueProcessingJobInput(
                seminarId = seminarId,
                type = ProcessingJobType.SUMMARY_DRAFT,
                inputAssetId = inputAssetId,
                inputPayloadJson = payloadJson,
                providerId = summaryProvider.providerId,
                providerVersion = summaryProvider.providerVersion,
            ),
        )
        enqueue(job, summaryDraftRequest(job.id, payloadJson), ExistingWorkPolicy.KEEP)
        return job
    }

    override suspend fun retry(jobId: Long): ProcessingJob? {
        val job = reconstructionRepository.requeueJob(jobId) ?: return null
        val request = requestFor(job) ?: return null
        enqueue(job, request, ExistingWorkPolicy.REPLACE)
        return job
    }

    override suspend fun cancel(jobId: Long) {
        workManager.cancelUniqueWork(workName(jobId))
        reconstructionRepository.markJobCancelled(jobId)
    }

    override fun recoverProcessingJobs() {
        recoveryScope.launch {
            reconstructionRepository.recoverInterruptedJobs().forEach { job ->
                requestFor(job)?.let { request ->
                    enqueue(job, request, ExistingWorkPolicy.KEEP)
                }
            }
        }
    }

    private fun enqueue(
        job: ProcessingJob,
        request: OneTimeWorkRequest,
        policy: ExistingWorkPolicy,
    ) {
        workManager.enqueueUniqueWork(workName(job.id), policy, request)
    }

    private suspend fun requestFor(job: ProcessingJob): OneTimeWorkRequest? {
        return when (job.type) {
            ProcessingJobType.IMAGE_ENHANCEMENT -> imageEnhancementRequest(job.id, ImageEnhancementOptions())
            ProcessingJobType.TEXT_OCR -> textOcrRequest(job.id, TextOcrLanguageMode.LATIN_AND_CHINESE)
            ProcessingJobType.TRANSCRIPTION -> transcriptionRequestFor(job)
            ProcessingJobType.SUMMARY_DRAFT -> job.inputPayloadJson?.let { summaryDraftRequest(job.id, it) }
            ProcessingJobType.NOTION_EXPORT_PREP -> null
        }
    }

    private fun imageEnhancementRequest(
        jobId: Long,
        options: ImageEnhancementOptions,
    ): OneTimeWorkRequest {
        return OneTimeWorkRequestBuilder<ProcessingWorker>()
            .setInputData(
                workDataOf(
                    ProcessingWorker.KEY_JOB_ID to jobId,
                    ProcessingWorker.KEY_OPERATION to ProcessingJobType.IMAGE_ENHANCEMENT.name,
                    ProcessingWorker.KEY_ROTATION_DEGREES to options.rotationDegrees,
                    ProcessingWorker.KEY_READABILITY to options.readability.name,
                    ProcessingWorker.KEY_JPEG_QUALITY to options.jpegQuality,
                    ProcessingWorker.KEY_HAS_CROP to (options.crop != null),
                    ProcessingWorker.KEY_CROP_LEFT to (options.crop?.left ?: 0f),
                    ProcessingWorker.KEY_CROP_TOP to (options.crop?.top ?: 0f),
                    ProcessingWorker.KEY_CROP_RIGHT to (options.crop?.right ?: 1f),
                    ProcessingWorker.KEY_CROP_BOTTOM to (options.crop?.bottom ?: 1f),
                    ProcessingWorker.KEY_HAS_PERSPECTIVE to (options.perspective != null),
                    ProcessingWorker.KEY_PERSPECTIVE_TOP_LEFT_X to (options.perspective?.topLeft?.x ?: 0f),
                    ProcessingWorker.KEY_PERSPECTIVE_TOP_LEFT_Y to (options.perspective?.topLeft?.y ?: 0f),
                    ProcessingWorker.KEY_PERSPECTIVE_TOP_RIGHT_X to (options.perspective?.topRight?.x ?: 1f),
                    ProcessingWorker.KEY_PERSPECTIVE_TOP_RIGHT_Y to (options.perspective?.topRight?.y ?: 0f),
                    ProcessingWorker.KEY_PERSPECTIVE_BOTTOM_RIGHT_X to (options.perspective?.bottomRight?.x ?: 1f),
                    ProcessingWorker.KEY_PERSPECTIVE_BOTTOM_RIGHT_Y to (options.perspective?.bottomRight?.y ?: 1f),
                    ProcessingWorker.KEY_PERSPECTIVE_BOTTOM_LEFT_X to (options.perspective?.bottomLeft?.x ?: 0f),
                    ProcessingWorker.KEY_PERSPECTIVE_BOTTOM_LEFT_Y to (options.perspective?.bottomLeft?.y ?: 1f),
                ),
            )
            .addTag(ProcessingWorker.WORK_TAG)
            .build()
    }

    private fun textOcrRequest(
        jobId: Long,
        languageMode: TextOcrLanguageMode,
    ): OneTimeWorkRequest {
        return OneTimeWorkRequestBuilder<ProcessingWorker>()
            .setInputData(
                workDataOf(
                    ProcessingWorker.KEY_JOB_ID to jobId,
                    ProcessingWorker.KEY_OPERATION to ProcessingJobType.TEXT_OCR.name,
                    ProcessingWorker.KEY_LANGUAGE_MODE to languageMode.name,
                ),
            )
            .addTag(ProcessingWorker.WORK_TAG)
            .build()
    }

    private suspend fun transcriptionRequestFor(job: ProcessingJob): OneTimeWorkRequest? {
        val sourceAsset = reconstructionRepository.getAsset(job.inputAssetId) ?: return null
        val recordingId = sourceAsset.sourceRecordingId ?: return null
        return transcriptionRequest(job.id, recordingId, TranscriptLanguageHint.AUTO)
    }

    private fun transcriptionRequest(
        jobId: Long,
        recordingId: Long,
        languageHint: TranscriptLanguageHint,
    ): OneTimeWorkRequest {
        return OneTimeWorkRequestBuilder<ProcessingWorker>()
            .setInputData(
                workDataOf(
                    ProcessingWorker.KEY_JOB_ID to jobId,
                    ProcessingWorker.KEY_OPERATION to ProcessingJobType.TRANSCRIPTION.name,
                    ProcessingWorker.KEY_RECORDING_ID to recordingId,
                    ProcessingWorker.KEY_LANGUAGE_HINT to languageHint.name,
                ),
            )
            .addTag(ProcessingWorker.WORK_TAG)
            .build()
    }

    private fun summaryDraftRequest(
        jobId: Long,
        payloadJson: String,
    ): OneTimeWorkRequest {
        return OneTimeWorkRequestBuilder<ProcessingWorker>()
            .setInputData(
                workDataOf(
                    ProcessingWorker.KEY_JOB_ID to jobId,
                    ProcessingWorker.KEY_OPERATION to ProcessingJobType.SUMMARY_DRAFT.name,
                    ProcessingWorker.KEY_SUMMARY_PAYLOAD_JSON to payloadJson,
                ),
            )
            .addTag(ProcessingWorker.WORK_TAG)
            .build()
    }

    private companion object {
        val json = Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
        }

        fun workName(jobId: Long): String = "seminararc-processing-$jobId"
    }
}
