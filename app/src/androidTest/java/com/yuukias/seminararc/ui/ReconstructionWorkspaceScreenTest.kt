package com.yuukias.seminararc.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.yuukias.seminararc.domain.model.OcrResult
import com.yuukias.seminararc.domain.model.ProcessingJob
import com.yuukias.seminararc.domain.model.ProcessingJobState
import com.yuukias.seminararc.domain.model.ProcessingJobType
import com.yuukias.seminararc.domain.model.SeminarAsset
import com.yuukias.seminararc.domain.model.SeminarAssetType
import com.yuukias.seminararc.domain.model.SeminarDetail
import com.yuukias.seminararc.domain.model.SeminarStatus
import com.yuukias.seminararc.ui.reconstruction.OcrStatusFilter
import com.yuukias.seminararc.ui.reconstruction.ReconstructionAssetUiItem
import com.yuukias.seminararc.ui.reconstruction.ReconstructionWorkspaceScreenContent
import com.yuukias.seminararc.ui.reconstruction.ReconstructionWorkspaceUiState
import com.yuukias.seminararc.ui.theme.SeminarArcTheme
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ReconstructionWorkspaceScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun processingControlsExposeQueuedRunningFailedCancelledStates() {
        val actions = mutableListOf<String>()
        composeRule.setContent {
            val snackbarHostState = remember { SnackbarHostState() }
            SeminarArcTheme {
                ReconstructionWorkspaceScreenContent(
                    uiState = readyState(),
                    snackbarHostState = snackbarHostState,
                    onBack = {},
                    onSearchQueryChanged = {},
                    onOcrStatusFilterChanged = {},
                    onKeySlidesOnlyChanged = {},
                    onKeySlideChanged = { _, _ -> },
                    onEditOcrResult = { _, _ -> actions += "edit" },
                    onEnhancePhoto = { actions += "enhance" },
                    onRunOcr = { actions += "ocr" },
                    onRetryJob = { jobId -> actions += "retry:$jobId" },
                    onCancelJob = { jobId -> actions += "cancel:$jobId" },
                )
            }
        }

        composeRule.onNodeWithText("OCR running").assertIsDisplayed()
        composeRule.onNodeWithText("Enhancement failed: transform failed").assertIsDisplayed()
        composeRule.onNodeWithText("Enhancement cancelled").assertIsDisplayed()
        composeRule.onNodeWithText("OCR").assertIsNotEnabled()
        composeRule.onNodeWithText("Enhance").assertIsEnabled()

        composeRule.onAllNodesWithText("Cancel")[0].performClick()
        composeRule.onAllNodesWithText("Retry")[0].performClick()

        assertEquals(listOf("cancel:2", "retry:4"), actions)
    }

    private fun readyState(): ReconstructionWorkspaceUiState.Ready {
        val asset = asset(id = 10L, displayName = "source.jpg")
        val cancelledAsset = asset(id = 11L, displayName = "cancelled.jpg")
        return ReconstructionWorkspaceUiState.Ready(
            detail = SeminarDetail(
                id = 1L,
                title = "Visual Reconstruction Seminar",
                speaker = "Prof. Ada",
                affiliation = "Seminar Lab",
                scheduledAt = NOW,
                location = "Room 1",
                abstractText = null,
                abstractAttachment = null,
                status = SeminarStatus.COMPLETED,
                sessionStartedAt = NOW,
                sessionEndedAt = NOW,
                rating = null,
                isFavorite = false,
                photoCount = 2,
                clipCount = 0,
                recordingDurationMs = null,
                timelinePreview = emptyList(),
            ),
            searchQuery = "",
            ocrStatusFilter = OcrStatusFilter.ALL,
            keySlidesOnly = false,
            items = listOf(
                ReconstructionAssetUiItem(
                    asset = asset,
                    absolutePhotoPath = null,
                    photoMissing = false,
                    ocrResult = ocrResult(asset.id),
                    jobs = listOf(
                        job(2L, ProcessingJobType.TEXT_OCR, ProcessingJobState.RUNNING, inputAssetId = asset.id, error = null),
                        job(3L, ProcessingJobType.IMAGE_ENHANCEMENT, ProcessingJobState.FAILED, inputAssetId = asset.id, error = "transform failed"),
                        job(4L, ProcessingJobType.IMAGE_ENHANCEMENT, ProcessingJobState.CANCELLED, inputAssetId = asset.id, error = null),
                    ),
                    isKeySlide = false,
                ),
                ReconstructionAssetUiItem(
                    asset = cancelledAsset,
                    absolutePhotoPath = null,
                    photoMissing = false,
                    ocrResult = null,
                    jobs = listOf(
                        job(
                            id = 4L,
                            type = ProcessingJobType.IMAGE_ENHANCEMENT,
                            state = ProcessingJobState.CANCELLED,
                            inputAssetId = cancelledAsset.id,
                            error = null,
                        ),
                    ),
                    isKeySlide = false,
                ),
            ),
            totalPhotoCount = 2,
            visiblePhotoCount = 2,
        )
    }

    private fun asset(
        id: Long,
        displayName: String,
    ): SeminarAsset {
        return SeminarAsset(
            id = id,
            seminarId = 1L,
            type = SeminarAssetType.PHOTO_ORIGINAL,
            originAssetId = null,
            sourceTimelineEventId = 7L,
            sourceRecordingId = null,
            sourceClipId = null,
            relativePath = "seminars/1/photos/$displayName",
            mimeType = "image/jpeg",
            displayName = displayName,
            createdAt = NOW,
            updatedAt = NOW,
        )
    }

    private fun ocrResult(assetId: Long): OcrResult {
        return OcrResult(
            id = 1L,
            seminarId = 1L,
            assetId = assetId,
            recognizedText = "Recognized text",
            editedText = null,
            blockJson = null,
            languageHint = "LATIN",
            confidence = null,
            providerId = "mlkit",
            providerVersion = "16.0.1",
            isEdited = false,
            createdAt = NOW,
            updatedAt = NOW,
        )
    }

    private fun job(
        id: Long,
        type: ProcessingJobType,
        state: ProcessingJobState,
        inputAssetId: Long,
        error: String?,
    ): ProcessingJob {
        return ProcessingJob(
            id = id,
            seminarId = 1L,
            type = type,
            state = state,
            inputAssetId = inputAssetId,
            outputAssetId = null,
            providerId = "provider",
            providerVersion = "1",
            createdAt = NOW.plusMillis(id),
            startedAt = NOW,
            completedAt = if (state == ProcessingJobState.RUNNING) null else NOW.plusMillis(10),
            retryCount = 1,
            isRetryable = true,
            errorMessage = error,
        )
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-08-31T08:00:00Z")
    }
}
