package com.yuukias.seminararc.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import com.yuukias.seminararc.domain.model.ReferenceCandidate
import com.yuukias.seminararc.domain.model.ReferenceCandidateStatus
import com.yuukias.seminararc.domain.model.ReferenceConfidenceBand
import com.yuukias.seminararc.domain.model.ReferenceLookupAttempt
import com.yuukias.seminararc.domain.model.ReferenceLookupAttemptState
import com.yuukias.seminararc.domain.model.ReferenceLookupProviderId
import com.yuukias.seminararc.domain.model.ReferenceLookupQuery
import com.yuukias.seminararc.domain.model.ReferenceLookupQueryType
import com.yuukias.seminararc.domain.model.ReferenceQueryPreview
import com.yuukias.seminararc.domain.model.SeminarBrief
import com.yuukias.seminararc.domain.model.SeminarDetail
import com.yuukias.seminararc.domain.model.SeminarStatus
import com.yuukias.seminararc.domain.repository.SaveSeminarBriefInput
import com.yuukias.seminararc.ui.reference.ReferenceEvidenceOption
import com.yuukias.seminararc.ui.reference.ReferenceReviewScreenContent
import com.yuukias.seminararc.ui.reference.ReferenceReviewUiState
import com.yuukias.seminararc.ui.theme.SeminarArcTheme
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ReferenceReviewScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComposeTestActivity>()

    @Test
    fun referenceReviewShowsPreviewAttemptsCandidatesAndSavesBrief() {
        val actions = mutableListOf<String>()
        var savedBrief: SaveSeminarBriefInput? = null
        composeRule.setContent {
            val snackbarHostState = remember { SnackbarHostState() }
            SeminarArcTheme {
                ReferenceReviewScreenContent(
                    uiState = readyState(),
                    snackbarHostState = snackbarHostState,
                    onBack = {},
                    onEvidenceOptionChanged = { id, selected -> actions += "evidence:$id:$selected" },
                    onManualTextChanged = { actions += "manual:$it" },
                    onBuildPreview = { actions += "preview" },
                    onRunLookup = { actions += "lookup" },
                    onCancelLookup = { actions += "cancel" },
                    onReviewCandidate = { id, status -> actions += "review:$id:$status" },
                    onEnsureBrief = { actions += "brief" },
                    onLinkSelectedKeySlides = { actions += "link-slides" },
                    onSaveBrief = { savedBrief = it },
                )
            }
        }

        composeRule.onNodeWithText("Evidence picker").assertIsDisplayed()
        composeRule.onNodeWithText("Query preview").assertIsDisplayed()

        composeRule.onNodeWithText("Run lookup").assertIsEnabled().performClick()
        composeRule.onNodeWithTag("referenceReviewList").performScrollToNode(hasTestTag("confirmReference-99"))
        composeRule.onNodeWithTag("confirmReference-99").performClick()
        composeRule.onNodeWithTag("rejectReference-99").performClick()
        composeRule.onNodeWithTag("reopenReference-99").performClick()
        composeRule.onNodeWithText("Link selected key slides").performScrollTo().assertIsEnabled().performClick()
        composeRule.onNodeWithText("Core question").performScrollTo().performTextInput("What is robust?")
        composeRule.onNodeWithText("Save brief").performScrollTo().performClick()

        assertEquals(
            listOf(
                "lookup",
                "review:99:CONFIRMED",
                "review:99:REJECTED",
                "review:99:PENDING",
                "link-slides",
            ),
            actions.take(5),
        )
        assertEquals("What is robust?", savedBrief?.coreQuestion)
    }

    private fun readyState(): ReferenceReviewUiState.Ready {
        return ReferenceReviewUiState.Ready(
            detail = detail(),
            evidenceOptions = listOf(
                ReferenceEvidenceOption(
                    id = "ocr:1",
                    sourceLabel = "OCR 10",
                    selectedText = "A Probabilistic Theory of Deep Learning DOI 10.1234/example",
                    sourceAssetId = 10L,
                    sourceId = 1L,
                ),
                ReferenceEvidenceOption(
                    id = "asset:10",
                    sourceLabel = "Key slide board.jpg",
                    selectedText = "board.jpg",
                    sourceAssetId = 10L,
                    sourceId = 10L,
                ),
            ),
            persistedEvidence = emptyList(),
            selectedOptionIds = setOf("ocr:1", "asset:10"),
            manualText = "",
            queryPreview = ReferenceQueryPreview(
                evidenceIds = listOf(1L),
                query = ReferenceLookupQuery(
                    provider = ReferenceLookupProviderId.CROSSREF,
                    queryType = ReferenceLookupQueryType.DOI,
                    doi = "10.1234/example",
                ),
                requestFingerprint = "fingerprint",
                selectedTextPreview = "A Probabilistic Theory of Deep Learning",
            ),
            attempts = listOf(
                attempt(ReferenceLookupAttemptState.RATE_LIMITED, "Crossref rate limited this lookup."),
                attempt(ReferenceLookupAttemptState.FAILED, "Provider unavailable"),
            ),
            candidates = listOf(candidate()),
            brief = SeminarBrief(
                id = 5L,
                seminarId = 1L,
                backgroundContext = "",
                coreQuestion = "",
                methods = "",
                mainResults = "",
                keyTakeaways = "",
                unresolvedQuestions = "",
                followUpActions = "",
                userNotes = "",
                createdAt = NOW,
                updatedAt = NOW,
            ),
            isLookupRunning = false,
            message = null,
        )
    }

    private fun detail() = SeminarDetail(
        id = 1L,
        title = "Reference Seminar",
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
        photoCount = 1,
        clipCount = 0,
        recordingDurationMs = null,
        timelinePreview = emptyList(),
    )

    private fun attempt(state: ReferenceLookupAttemptState, error: String?) = ReferenceLookupAttempt(
        id = state.ordinal.toLong(),
        seminarId = 1L,
        provider = ReferenceLookupProviderId.CROSSREF,
        queryType = ReferenceLookupQueryType.DOI,
        evidenceIdsJson = "[1]",
        queryPreview = "DOI: 10.1234/example",
        requestFingerprint = "fp-${state.name}",
        state = state,
        httpStatus = if (state == ReferenceLookupAttemptState.RATE_LIMITED) 429 else 503,
        resultCount = 0,
        cacheHit = false,
        errorMessage = error,
        retryAfterEpochMs = null,
        createdAt = NOW,
        completedAt = NOW,
    )

    private fun candidate() = ReferenceCandidate(
        id = 99L,
        seminarId = 1L,
        canonicalDoi = "10.1234/example",
        normalizedDoi = "10.1234/example",
        title = "A Probabilistic Theory of Deep Learning",
        normalizedTitle = "a probabilistic theory of deep learning",
        authorsJson = """["Alice Example","Bob Example"]""",
        publicationYear = 2024,
        venue = "Proceedings",
        sourceTitle = "Proceedings",
        publicationType = "proceedings-article",
        landingPageUrl = "https://doi.org/10.1234/example",
        openAccessUrl = null,
        licenseUrl = null,
        matcherVersion = "reference-match-v1",
        matchScore = 100,
        confidenceBand = ReferenceConfidenceBand.HIGH_CONFIDENCE,
        matchReasonsJson = """[{"component":"doi","points":100,"text":"Normalized DOI matched provider metadata."}]""",
        status = ReferenceCandidateStatus.PENDING,
        evidenceFingerprint = "fingerprint",
        lookupAttemptId = 1L,
        createdAt = NOW,
        updatedAt = NOW,
        reviewedAt = null,
    )

    private companion object {
        val NOW: Instant = Instant.parse("2026-09-01T08:00:00Z")
    }
}
