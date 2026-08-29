package com.yuukias.seminararc.media.clip

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.yuukias.seminararc.data.storage.MediaStorageManager
import com.yuukias.seminararc.domain.model.RecordingState
import com.yuukias.seminararc.domain.repository.ClipRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

class ClipGenerationWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val clipId = inputData.getLong(KEY_CLIP_ID, -1L)
        if (clipId <= 0L) return Result.failure()
        val dependencies = EntryPointAccessors.fromApplication(
            applicationContext,
            ClipGenerationWorkerEntryPoint::class.java,
        )
        val repository = dependencies.clipRepository()
        val storage = dependencies.mediaStorageManager()
        val generator = dependencies.clipGenerator()
        val clip = repository.getClip(clipId) ?: return Result.failure()
        val recording = repository.getRecordingForClip(clip)
            ?: return fail(repository, clipId, "Source recording row is missing.")
        val durationMs = recording.durationMs
        if (recording.state != RecordingState.COMPLETED || durationMs == null) {
            return Result.retry()
        }
        val clippedEndOffsetMs = clip.endOffsetMs.coerceAtMost(durationMs)
        if (clippedEndOffsetMs <= clip.startOffsetMs) {
            return fail(repository, clipId, "Clip interval is outside the completed recording.")
        }
        val source = storage.resolveReadableRelativeFile(recording.filePath)
            ?: return fail(repository, clipId, "Source recording file is missing.")
        val output = storage.createClipOutputFile(clip.seminarId, clip.id)
        repository.markProcessing(clipId)
        return when (val result = generator.generate(source, output.file, clip.startOffsetMs, clippedEndOffsetMs)) {
            ClipGenerationResult.Generated -> {
                if (storage.resolveReadableRelativeFile(output.relativePath) == null) {
                    storage.deleteRelativeFile(output.relativePath)
                    fail(repository, clipId, "Generated clip file is not readable.")
                } else {
                    repository.markReady(clipId, output.relativePath)
                    Result.success()
                }
            }
            is ClipGenerationResult.Failed -> {
                storage.deleteRelativeFile(output.relativePath)
                fail(repository, clipId, result.message)
            }
            else -> fail(repository, clipId, "Clip generation failed.")
        }
    }

    private suspend fun fail(
        repository: ClipRepository,
        clipId: Long,
        message: String,
    ): Result {
        repository.markFailed(clipId, message)
        return Result.failure()
    }

    companion object {
        const val KEY_CLIP_ID = "clip_id"
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ClipGenerationWorkerEntryPoint {
    fun clipRepository(): ClipRepository
    fun mediaStorageManager(): MediaStorageManager
    fun clipGenerator(): ClipGenerator
}
