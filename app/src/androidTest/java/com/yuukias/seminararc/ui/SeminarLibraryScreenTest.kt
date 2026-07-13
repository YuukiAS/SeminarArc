package com.yuukias.seminararc.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.yuukias.seminararc.domain.model.SeminarListFilter
import com.yuukias.seminararc.domain.model.SeminarStatus
import com.yuukias.seminararc.domain.model.SeminarSummary
import com.yuukias.seminararc.ui.library.SeminarLibraryScreenContent
import com.yuukias.seminararc.ui.library.SeminarLibraryUiState
import com.yuukias.seminararc.ui.theme.SeminarArcTheme
import java.time.Instant
import org.junit.Rule
import org.junit.Test

class SeminarLibraryScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyState_isVisible() {
        composeRule.setContent {
            SeminarArcTheme {
                SeminarLibraryScreenContent(
                    uiState = SeminarLibraryUiState.Ready(
                        query = "",
                        filter = SeminarListFilter.ALL,
                        seminars = emptyList(),
                    ),
                    onCreateSeminar = {},
                    onOpenSeminar = { _ -> },
                    onQueryChanged = { _ -> },
                    onFilterChanged = { _ -> },
                )
            }
        }

        composeRule.onNodeWithText("Your seminar library is empty").assertIsDisplayed()
    }

    @Test
    fun seminarRow_rendersRealTitle() {
        composeRule.setContent {
            SeminarArcTheme {
                SeminarLibraryScreenContent(
                    uiState = SeminarLibraryUiState.Ready(
                        query = "",
                        filter = SeminarListFilter.ALL,
                        seminars = listOf(
                            SeminarSummary(
                                id = 1L,
                                title = "Domain Adaptation for Multi-Center MRI Segmentation",
                                speaker = "Prof. Lina Chen",
                                scheduledAt = Instant.parse("2026-07-13T08:00:00Z"),
                                location = "Yasumoto 303",
                                status = SeminarStatus.DRAFT,
                                isFavorite = false,
                                rating = null,
                                photoCount = 0,
                                clipCount = 0,
                            ),
                        ),
                    ),
                    onCreateSeminar = {},
                    onOpenSeminar = { _ -> },
                    onQueryChanged = { _ -> },
                    onFilterChanged = { _ -> },
                )
            }
        }

        composeRule.onNodeWithText("Domain Adaptation for Multi-Center MRI Segmentation").assertIsDisplayed()
    }
}
