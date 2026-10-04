package com.yuukias.seminararc.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.yuukias.seminararc.ui.editor.SeminarEditorScreenContent
import com.yuukias.seminararc.ui.editor.SeminarEditorUiState
import com.yuukias.seminararc.ui.theme.SeminarArcTheme
import org.junit.Rule
import org.junit.Test

class SeminarEditorScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComposeTestActivity>()

    @Test
    fun validationError_scrollsBackIntoViewFromSaveButton() {
        composeRule.setContent {
            var uiState by remember { mutableStateOf(editorState()) }
            SeminarArcTheme {
                SeminarEditorScreenContent(
                    uiState = uiState,
                    snackbarHostState = remember { SnackbarHostState() },
                    onBack = {},
                    onTitleChanged = {},
                    onSpeakerChanged = {},
                    onAffiliationChanged = {},
                    onLocationChanged = {},
                    onAbstractTextChanged = {},
                    onScheduledAtChanged = {},
                    onPdfSelected = {},
                    onRemovePdfClicked = {},
                    onSave = { uiState = uiState.copy(errorMessage = "Title is required.") },
                )
            }
        }

        composeRule.onNodeWithText("Save draft").performScrollTo().assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Title is required.").assertIsDisplayed()
    }

    private fun editorState() = SeminarEditorUiState.Editing(
        seminarId = null,
        title = "",
        speaker = "",
        affiliation = "",
        scheduledAt = null,
        location = "",
        abstractText = "",
        attachment = null,
        pendingAttachmentUri = null,
        removeExistingAttachment = false,
        isSaving = false,
        errorMessage = null,
    )
}
