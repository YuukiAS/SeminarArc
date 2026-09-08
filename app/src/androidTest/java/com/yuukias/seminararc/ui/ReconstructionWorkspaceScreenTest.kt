package com.yuukias.seminararc.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import com.yuukias.seminararc.domain.model.FormulaRegion
import com.yuukias.seminararc.domain.model.FormulaResult
import com.yuukias.seminararc.domain.model.FormulaResultState
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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ReconstructionWorkspaceScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComposeTestActivity>()

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
                    onOpenReferenceReview = {},
                    onOpenTranscriptReview = { actions += "transcripts:$it" },
                    onSearchQueryChanged = {},
                    onOcrStatusFilterChanged = {},
                    onKeySlidesOnlyChanged = {},
                    onKeySlideChanged = { _, _ -> },
                    onEditOcrResult = { _, _ -> actions += "edit" },
                    onAddFormulaRegion = { _, _, _, _, _, _ -> },
                    onUpdateFormulaRegion = { _, _, _, _, _, _ -> },
                    onDeleteFormulaRegion = {},
                    onSaveFormulaLatex = { _, _ -> },
                    onEnhancePhoto = { actions += "enhance" },
                    onRunOcr = { actions += "ocr" },
                    onRetryJob = { jobId -> actions += "retry:$jobId" },
                    onCancelJob = { jobId -> actions += "cancel:$jobId" },
                )
            }
        }

        composeRule.onNodeWithText("OCR running").assertIsDisplayed()
        composeRule.onNodeWithText("Review transcripts").assertIsDisplayed()
        composeRule.onNodeWithText("Enhancement failed: transform failed").assertIsDisplayed()
        composeRule.onNodeWithText("Enhancement cancelled").assertIsDisplayed()
        composeRule.onNodeWithText("OCR").assertIsNotEnabled()
        composeRule.onNodeWithText("Enhance").assertIsEnabled()

        composeRule.onAllNodesWithText("Cancel")[0].performClick()
        composeRule.onAllNodesWithText("Retry")[0].performClick()

        assertEquals(listOf("cancel:2", "retry:4"), actions)
    }

    @Test
    fun formulaRegionControlsExposeSavedRegionsAndAddAction() {
        val actions = mutableListOf<String>()
        composeRule.setContent {
            val snackbarHostState = remember { SnackbarHostState() }
            SeminarArcTheme {
                ReconstructionWorkspaceScreenContent(
                    uiState = readyState(),
                    snackbarHostState = snackbarHostState,
                    onBack = {},
                    onOpenReferenceReview = {},
                    onOpenTranscriptReview = {},
                    onSearchQueryChanged = {},
                    onOcrStatusFilterChanged = {},
                    onKeySlidesOnlyChanged = {},
                    onKeySlideChanged = { _, _ -> },
                    onEditOcrResult = { _, _ -> },
                    onAddFormulaRegion = { assetId, label, x, y, width, height ->
                        actions += "formula:$assetId:$label:$x:$y:$width:$height"
                    },
                    onUpdateFormulaRegion = { regionId, label, x, y, width, height ->
                        actions += "update-formula:$regionId:$label:$x:$y:$width:$height"
                    },
                    onDeleteFormulaRegion = { regionId -> actions += "delete-formula:$regionId" },
                    onSaveFormulaLatex = { regionId, latex -> actions += "latex:$regionId:$latex" },
                    onEnhancePhoto = {},
                    onRunOcr = {},
                    onRetryJob = {},
                    onCancelJob = {},
                )
            }
        }

        composeRule.onNodeWithText("Formula regions").assertIsDisplayed()
        composeRule.onNodeWithText("Main equation: x=0.1, y=0.2, w=0.7, h=0.25").assertIsDisplayed()
        composeRule.onNodeWithText("Formula ready: E = mc^2").assertIsDisplayed()
        composeRule.onAllNodesWithText("Save formula region")[0].performClick()
        composeRule.onAllNodesWithText("Edit crop")[0].performClick()
        composeRule.onAllNodesWithText("Update formula region")[0].performClick()
        composeRule.onAllNodesWithText("Queue LaTeX")[0].performClick()
        composeRule.onAllNodesWithText("Delete")[0].performClick()

        assertEquals(
            listOf(
                "formula:10:Formula:0.1:0.1:0.8:0.3",
                "update-formula:20:Main equation:0.1:0.2:0.7:0.25",
                "latex:20:E = mc^2",
                "delete-formula:20",
            ),
            actions,
        )
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
                    formulaRegions = listOf(formulaRegion(asset.id)),
                    formulaResults = listOf(formulaResult()),
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
                    formulaRegions = emptyList(),
                    formulaResults = emptyList(),
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

    @Test
    fun photoPreviewExposesFormulaOverlaySemanticsWhenPhotoIsReadable() {
        val image = android.graphics.Bitmap.createBitmap(4, 4, android.graphics.Bitmap.Config.ARGB_8888)
        val file = java.io.File.createTempFile("seminararc-formula-overlay", ".png").apply {
            outputStream().use { output -> image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output) }
        }
        composeRule.setContent {
            val snackbarHostState = remember { SnackbarHostState() }
            SeminarArcTheme {
                ReconstructionWorkspaceScreenContent(
                    uiState = readyStateWithPhoto(file.absolutePath),
                    snackbarHostState = snackbarHostState,
                    onBack = {},
                    onOpenReferenceReview = {},
                    onOpenTranscriptReview = {},
                    onSearchQueryChanged = {},
                    onOcrStatusFilterChanged = {},
                    onKeySlidesOnlyChanged = {},
                    onKeySlideChanged = { _, _ -> },
                    onEditOcrResult = { _, _ -> },
                    onAddFormulaRegion = { _, _, _, _, _, _ -> },
                    onUpdateFormulaRegion = { _, _, _, _, _, _ -> },
                    onDeleteFormulaRegion = {},
                    onSaveFormulaLatex = { _, _ -> },
                    onEnhancePhoto = {},
                    onRunOcr = {},
                    onRetryJob = {},
                    onCancelJob = {},
                )
            }
        }

        composeRule
            .onNodeWithContentDescription("Seminar photo with 1 formula regions. Drag to draft formula region.")
            .assertIsDisplayed()
        file.delete()
    }

    @Test
    fun photoPreviewDragUpdatesFormulaDraftBeforeExplicitSave() {
        val actions = mutableListOf<String>()
        val image = android.graphics.Bitmap.createBitmap(4, 4, android.graphics.Bitmap.Config.ARGB_8888)
        val file = java.io.File.createTempFile("seminararc-formula-drag", ".png").apply {
            outputStream().use { output -> image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output) }
        }
        composeRule.setContent {
            val snackbarHostState = remember { SnackbarHostState() }
            SeminarArcTheme {
                ReconstructionWorkspaceScreenContent(
                    uiState = readyStateWithPhoto(file.absolutePath),
                    snackbarHostState = snackbarHostState,
                    onBack = {},
                    onOpenReferenceReview = {},
                    onOpenTranscriptReview = {},
                    onSearchQueryChanged = {},
                    onOcrStatusFilterChanged = {},
                    onKeySlidesOnlyChanged = {},
                    onKeySlideChanged = { _, _ -> },
                    onEditOcrResult = { _, _ -> },
                    onAddFormulaRegion = { assetId, label, x, y, width, height ->
                        actions += "formula:$assetId:$label:$x:$y:$width:$height"
                    },
                    onUpdateFormulaRegion = { _, _, _, _, _, _ -> },
                    onDeleteFormulaRegion = {},
                    onSaveFormulaLatex = { _, _ -> },
                    onEnhancePhoto = {},
                    onRunOcr = {},
                    onRetryJob = {},
                    onCancelJob = {},
                )
            }
        }

        composeRule
            .onNodeWithContentDescription("Seminar photo with 1 formula regions. Drag to draft formula region.")
            .performTouchInput {
                swipe(start = Offset(12f, 12f), end = Offset(132f, 84f), durationMillis = 300)
            }
        composeRule.onAllNodesWithText("Save formula region")[0].performClick()

        val savedAction = actions.single()
        assertTrue(savedAction.startsWith("formula:10:Formula:"))
        assertNotEquals("formula:10:Formula:0.1:0.1:0.8:0.3", savedAction)
        file.delete()
    }

    private fun readyStateWithPhoto(path: String): ReconstructionWorkspaceUiState.Ready {
        val state = readyState()
        return state.copy(
            items = listOf(
                state.items.first().copy(absolutePhotoPath = path),
            ),
            totalPhotoCount = 1,
            visiblePhotoCount = 1,
        )
    }

    private fun formulaRegion(assetId: Long): FormulaRegion {
        return FormulaRegion(
            id = 20L,
            seminarId = 1L,
            sourceAssetId = assetId,
            sourcePhotoPath = "seminars/1/photos/source.jpg",
            normalizedX = 0.1f,
            normalizedY = 0.2f,
            normalizedWidth = 0.7f,
            normalizedHeight = 0.25f,
            rotationDegrees = 0,
            label = "Main equation",
            createdAt = NOW,
            updatedAt = NOW,
        )
    }

    private fun formulaResult(): FormulaResult {
        return FormulaResult(
            id = 30L,
            seminarId = 1L,
            regionId = 20L,
            providerId = "manual-formula",
            providerVersion = "1",
            state = FormulaResultState.READY,
            latex = "E = mc^2",
            confidence = 1f,
            isEdited = true,
            errorMessage = null,
            provenanceJson = "{}",
            createdAt = NOW,
            updatedAt = NOW,
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
            inputPayloadJson = null,
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
