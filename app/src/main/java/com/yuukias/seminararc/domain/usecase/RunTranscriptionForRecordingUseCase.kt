package com.yuukias.seminararc.domain.usecase

import com.yuukias.seminararc.data.storage.MediaStorageManager
import com.yuukias.seminararc.domain.model.ProcessingJob
import com.yuukias.seminararc.domain.model.ProcessingJobType
import com.yuukias.seminararc.domain.model.RecordingState
import com.yuukias.seminararc.domain.model.Transcript
import com.yuukias.seminararc.domain.model.TranscriptLanguageHint
import com.yuukias.seminararc.domain.model.TranscriptSegment
import com.yuukias.seminararc.domain.model.TranscriptSourceType
import com.yuukias.seminararc.domain.model.TranscriptState
import com.yuukias.seminararc.domain.repository.CreateTranscriptInput
import com.yuukias.seminararc.domain.repository.EnqueueProcessingJobInput
import com.yuukias.seminararc.domain.repository.RecordingRepository
import com.yuukias.seminararc.domain.repository.ReconstructionRepository
import com.yuukias.seminararc.domain.repository.TranscriptRepository
import com.yuukias.seminararc.domain.transcription.TranscriptionProvider
import com.yuukias.seminararc.domain.transcription.TranscriptionRequest
import com.yuukias.seminararc.domain.transcription.TranscriptionResult
import javax.inject.Inject

class RunTranscriptionForRecordingUseCase @Inject constructor(
    private val recordingRepository: RecordingRepository,
    private val reconstructionRepository: ReconstructionRepository,
    private val transcriptRepository: TranscriptRepository,
    private val mediaStorageManager: MediaStorageManager,
    private val transcriptionProvider: TranscriptionProvider,
) {
    suspend operator fun invoke(
        recordingId: Long,
        languageHint: TranscriptLanguageHint = TranscriptLanguageHint.AUTO,
        existingJobId: Long? = null,
    ): RunTranscriptionResult {
        val recording = recordingRepository.getRecording(recordingId)
            ?: return RunTranscriptionResult.Failed("Recording was not found.")
        if (recording.state != RecordingState.COMPLETED) {
            return RunTranscriptionResult.Failed("Only completed recordings can be transcribed.")
        }
        val source = mediaStorageManager.resolveReadableRelativeFile(recording.filePath)
            ?: return RunTranscriptionResult.Failed("Recording file is not readable.")
        val sourceAsset = reconstructionRepository.getAssetByRelativePath(recording.filePath)
            ?: return RunTranscriptionResult.Failed("Recording asset was not found.")
        transcriptRepository.getLatestTranscriptForRecording(
            seminarId = recording.seminarId,
            recordingId = recording.id,
            providerId = transcriptionProvider.providerId,
        )?.takeIf { it.state == TranscriptState.READY }?.let { existing ->
            return RunTranscriptionResult.Transcribed(
                transcript = existing,
                segments = transcriptRepository.getSegments(existing.id),
                job = null,
            )
        }
        val job = existingJobId
            ?.let { jobId ->
                reconstructionRepository.getJob(jobId)?.takeIf { existing ->
                    existing.seminarId == recording.seminarId &&
                        existing.type == ProcessingJobType.TRANSCRIPTION &&
                        existing.inputAssetId == sourceAsset.id &&
                        existing.providerId == transcriptionProvider.providerId
                }
            }
            ?: reconstructionRepository.enqueueJob(
                EnqueueProcessingJobInput(
                    seminarId = recording.seminarId,
                    type = ProcessingJobType.TRANSCRIPTION,
                    inputAssetId = sourceAsset.id,
                    providerId = transcriptionProvider.providerId,
                    providerVersion = transcriptionProvider.providerVersion,
                ),
            )
        reconstructionRepository.markJobRunning(job.id)
        val transcript = transcriptRepository.createTranscript(
            CreateTranscriptInput(
                seminarId = recording.seminarId,
                recordingId = recording.id,
                providerId = transcriptionProvider.providerId,
                providerVersion = transcriptionProvider.providerVersion,
                languageHint = languageHint,
                sourceType = TranscriptSourceType.RECORDING,
                sourceAssetId = sourceAsset.id,
            ),
        )
        transcriptRepository.markTranscriptRunning(transcript.id)
        val request = TranscriptionRequest(
            seminarId = recording.seminarId,
            recordingId = recording.id,
            sourceAudio = source,
            languageHint = languageHint,
            sourceAssetId = sourceAsset.id,
        )
        return when (val result = transcriptionProvider.transcribe(request)) {
            is TranscriptionResult.Transcribed -> {
                val segments = transcriptRepository.saveTranscriptSegments(
                    transcriptId = transcript.id,
                    segments = result.recognition.segments,
                )
                val ready = transcriptRepository.markTranscriptReady(
                    transcriptId = transcript.id,
                    languageHint = result.recognition.languageHint,
                ) ?: transcript
                reconstructionRepository.markJobSucceeded(job.id, outputAssetId = null)
                RunTranscriptionResult.Transcribed(
                    transcript = ready,
                    segments = segments,
                    job = job,
                )
            }
            is TranscriptionResult.Failed -> {
                transcriptRepository.markTranscriptFailed(transcript.id, result.message)
                reconstructionRepository.markJobFailed(job.id, result.message, result.isRetryable)
                RunTranscriptionResult.Failed(result.message)
            }
        }
    }
}

sealed interface RunTranscriptionResult {
    data class Transcribed(
        val transcript: Transcript,
        val segments: List<TranscriptSegment>,
        val job: ProcessingJob?,
    ) : RunTranscriptionResult

    data class Failed(val message: String) : RunTranscriptionResult
}
