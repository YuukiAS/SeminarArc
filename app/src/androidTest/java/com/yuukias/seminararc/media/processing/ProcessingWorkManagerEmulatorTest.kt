package com.yuukias.seminararc.media.processing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yuukias.seminararc.data.local.entity.SeminarAssetEntity
import com.yuukias.seminararc.data.local.entity.SeminarEntity
import com.yuukias.seminararc.domain.image.ImageEnhancementOptions
import com.yuukias.seminararc.domain.model.ProcessingJobState
import com.yuukias.seminararc.domain.model.SeminarAssetType
import com.yuukias.seminararc.domain.model.SeminarStatus
import com.yuukias.seminararc.domain.ocr.TextOcrLanguageMode
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProcessingWorkManagerEmulatorTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var dependencies: ProcessingWorkerEntryPoint

    @Before
    fun setUp() {
        dependencies = EntryPointAccessors.fromApplication(
            context.applicationContext,
            ProcessingWorkerEntryPoint::class.java,
        )
        dependencies.appDatabase().clearAllTables()
    }

    @Test
    fun workManagerQueueRunsOcrEnhancementDuplicateRetryCancelAndPersistence() = runBlocking {
        val seminarId = insertSeminar()
        val photo = createTextImage(seminarId, "workmanager-source.jpg", "QUEUE OCR\nSeminarArc")
        val assetId = insertPhotoAsset(seminarId, photo.relativePath)

        val firstOcrJob = dependencies.processingWorkScheduler().enqueueTextOcr(assetId, TextOcrLanguageMode.LATIN)
        val duplicateOcrJob = dependencies.processingWorkScheduler().enqueueTextOcr(assetId, TextOcrLanguageMode.LATIN)
        assertNotNull(firstOcrJob)
        assertEquals(firstOcrJob?.id, duplicateOcrJob?.id)

        val completedOcr = awaitJob(firstOcrJob!!.id, ProcessingJobState.SUCCEEDED)
        assertEquals(assetId, completedOcr.outputAssetId)
        val persistedOcr = dependencies.reconstructionRepository().observeOcrResultsForSeminar(seminarId).firstValue()
        assertTrue(persistedOcr.single().recognizedText.isNotBlank())

        val enhancementJob = dependencies.processingWorkScheduler().enqueueImageEnhancement(assetId, ImageEnhancementOptions())
        val completedEnhancement = awaitJob(enhancementJob!!.id, ProcessingJobState.SUCCEEDED)
        assertNotNull(completedEnhancement.outputAssetId)
        assertNotEquals(assetId, completedEnhancement.outputAssetId)
        val enhancedAsset = dependencies.reconstructionRepository().getAsset(completedEnhancement.outputAssetId!!)
        assertNotNull(enhancedAsset)
        assertTrue(dependencies.mediaStorageManager().resolveReadableRelativeFile(enhancedAsset!!.relativePath!!) != null)
        assertTrue(dependencies.mediaStorageManager().resolveReadableRelativeFile(photo.relativePath) != null)

        val missingAssetId = insertPhotoAsset(seminarId, "seminars/$seminarId/photos/missing.jpg")
        val failedJob = dependencies.processingWorkScheduler().enqueueTextOcr(missingAssetId, TextOcrLanguageMode.LATIN)
        val failed = awaitJob(failedJob!!.id, ProcessingJobState.FAILED)
        assertTrue(failed.isRetryable)
        val retried = dependencies.processingWorkScheduler().retry(failed.id)
        assertEquals(ProcessingJobState.QUEUED, retried?.state)
        val failedAgain = awaitJob(failed.id, ProcessingJobState.FAILED)
        assertTrue(failedAgain.retryCount >= 2)

        val cancelJob = dependencies.processingWorkScheduler().enqueueImageEnhancement(missingAssetId, ImageEnhancementOptions())
        dependencies.processingWorkScheduler().cancel(cancelJob!!.id)
        val cancelled = dependencies.reconstructionRepository().getJob(cancelJob.id)
        assertEquals(ProcessingJobState.CANCELLED, cancelled?.state)
    }

    private suspend fun insertSeminar(): Long {
        val now = Instant.parse("2026-08-31T08:00:00Z")
        return dependencies.appDatabase().seminarDao().insertSeminar(
            SeminarEntity(
                title = "Processing Queue Test",
                speaker = "Test Speaker",
                affiliation = null,
                scheduledAt = now,
                location = "Emulator",
                abstractText = null,
                abstractPdfPath = null,
                status = SeminarStatus.COMPLETED,
                rating = null,
                isFavorite = false,
                createdAt = now,
                updatedAt = now,
                sessionStartedAt = now,
                sessionEndedAt = now,
            ),
        )
    }

    private suspend fun insertPhotoAsset(
        seminarId: Long,
        relativePath: String,
    ): Long {
        val now = Instant.parse("2026-08-31T08:01:00Z")
        return dependencies.appDatabase().reconstructionDao().insertAsset(
            SeminarAssetEntity(
                seminarId = seminarId,
                type = SeminarAssetType.PHOTO_ORIGINAL,
                originAssetId = null,
                sourceTimelineEventId = null,
                sourceRecordingId = null,
                sourceClipId = null,
                relativePath = relativePath,
                mimeType = "image/jpeg",
                displayName = File(relativePath).name,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    private fun createTextImage(
        seminarId: Long,
        name: String,
        text: String,
    ): TestPhoto {
        val relativePath = "seminars/$seminarId/photos/$name"
        val file = File(context.filesDir, relativePath)
        file.parentFile?.mkdirs()
        val bitmap = Bitmap.createBitmap(900, 520, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 64f
        }
        text.lines().forEachIndexed { index, line ->
            canvas.drawText(line, 60f, 150f + (index * 100f), paint)
        }
        file.outputStream().use { output -> bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output) }
        bitmap.recycle()
        return TestPhoto(relativePath)
    }

    private suspend fun awaitJob(
        jobId: Long,
        state: ProcessingJobState,
    ): com.yuukias.seminararc.domain.model.ProcessingJob {
        repeat(80) {
            val job = dependencies.reconstructionRepository().getJob(jobId)
            if (job?.state == state) return job
            delay(250)
        }
        error("Job $jobId did not reach $state.")
    }
}

private data class TestPhoto(val relativePath: String)

private suspend fun <T> kotlinx.coroutines.flow.Flow<List<T>>.firstValue(): List<T> {
    return first()
}
