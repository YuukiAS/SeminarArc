package com.yuukias.seminararc.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.yuukias.seminararc.domain.model.SeminarDetail
import com.yuukias.seminararc.domain.model.SeminarStatus
import com.yuukias.seminararc.ui.detail.SeminarDetailScreenContent
import com.yuukias.seminararc.ui.detail.SeminarDetailUiState
import com.yuukias.seminararc.ui.detail.RecordingPlaybackUiState
import com.yuukias.seminararc.ui.theme.SeminarArcTheme
import java.time.Instant
import org.junit.Rule
import org.junit.Test

class SeminarDetailScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun noAbstractState_isVisible() {
        composeRule.setContent {
            SeminarArcTheme {
                SeminarDetailScreenContent(
                    uiState = SeminarDetailUiState.Ready(
                        detail = SeminarDetail(
                            id = 1L,
                            title = "Domain Adaptation Seminar",
                            speaker = "Prof. Lina Chen",
                            affiliation = "CUHK",
                            scheduledAt = Instant.parse("2026-07-13T08:00:00Z"),
                            location = "Yasumoto 303",
                            abstractText = null,
                            abstractAttachment = null,
                            status = SeminarStatus.DRAFT,
                            sessionStartedAt = null,
                            sessionEndedAt = null,
                            rating = null,
                            isFavorite = false,
                            photoCount = 0,
                            clipCount = 0,
                            recordingDurationMs = null,
                            timelinePreview = emptyList(),
                        ),
                        isDeleting = false,
                        showDeleteDialog = false,
                        isStartingRecording = false,
                        isExporting = false,
                        exportMessage = null,
                        recordingErrorMessage = null,
                        recordingPlayback = RecordingPlaybackUiState.NoRecording,
                    ),
                    onBack = {},
                    onEdit = { _ -> },
                    onFavoriteToggle = {},
                    onRatingSelected = { _ -> },
                    onDeleteDialogChanged = { _ -> },
                    onDeleteConfirmed = {},
                    onStartRecording = {},
                    onStartPhotosOnly = {},
                    onOpenTimeline = {},
                    onSaveMarkdown = {},
                    onSaveZip = {},
                    onShareMarkdown = {},
                    onShareZip = {},
                    onPlaybackPlayPause = {},
                    onPlaybackSeek = { _ -> },
                )
            }
        }

        composeRule.onNodeWithText("No abstract PDF attached. This seminar remains valid and can be completed without it.").assertIsDisplayed()
    }
}
