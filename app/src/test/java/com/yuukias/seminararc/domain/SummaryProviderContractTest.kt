package com.yuukias.seminararc.domain

import com.yuukias.seminararc.domain.summary.SummaryDraftContent
import com.yuukias.seminararc.domain.summary.SummaryProvider
import com.yuukias.seminararc.domain.summary.SummaryRequest
import com.yuukias.seminararc.domain.summary.SummaryResult
import com.yuukias.seminararc.domain.summary.SummaryTranscriptWindow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SummaryProviderContractTest {
    @Test
    fun fakeProviderDraftsFromSelectedInputsOnly() = runTest {
        val provider = FakeSummaryProvider(
            SummaryResult.Drafted(
                SummaryDraftContent(
                    backgroundContext = "background",
                    coreQuestion = "question",
                    methods = "methods",
                    mainResults = "results",
                    keyTakeaways = "takeaways",
                    unresolvedQuestions = "questions",
                    followUpActions = "actions",
                    userNotes = "keep manual notes",
                    provenanceJson = """{"fixture":"contract"}""",
                ),
            ),
        )
        val request = SummaryRequest(
            seminarId = 1L,
            title = "Seminar",
            speaker = "Speaker",
            affiliation = "Lab",
            abstractText = null,
            transcriptWindows = listOf(
                SummaryTranscriptWindow(
                    segmentId = 7L,
                    startOffsetMs = 10_000L,
                    endOffsetMs = 20_000L,
                    text = "Selected transcript window.",
                ),
            ),
            referenceTitles = listOf("Reference A"),
            keySlideCaptions = listOf("Key slide"),
            userNotes = "keep manual notes",
            inputFingerprint = "summary-input-v1",
        )

        val result = provider.draft(request) as SummaryResult.Drafted

        assertEquals("fake-summary", provider.providerId)
        assertEquals(request, provider.lastRequest)
        assertEquals("keep manual notes", result.content.userNotes)
        assertEquals("""{"fixture":"contract"}""", result.content.provenanceJson)
    }

    @Test
    fun requestRejectsInvalidIdentityAndBlankFingerprint() {
        assertThrows(IllegalArgumentException::class.java) {
            SummaryRequest(
                seminarId = 0L,
                title = "Seminar",
                speaker = null,
                affiliation = null,
                abstractText = null,
                transcriptWindows = emptyList(),
                referenceTitles = emptyList(),
                keySlideCaptions = emptyList(),
                userNotes = "",
                inputFingerprint = "fingerprint",
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            SummaryRequest(
                seminarId = 1L,
                title = " ",
                speaker = null,
                affiliation = null,
                abstractText = null,
                transcriptWindows = emptyList(),
                referenceTitles = emptyList(),
                keySlideCaptions = emptyList(),
                userNotes = "",
                inputFingerprint = "fingerprint",
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            SummaryRequest(
                seminarId = 1L,
                title = "Seminar",
                speaker = null,
                affiliation = null,
                abstractText = null,
                transcriptWindows = emptyList(),
                referenceTitles = emptyList(),
                keySlideCaptions = emptyList(),
                userNotes = "",
                inputFingerprint = " ",
            )
        }
    }

    @Test
    fun transcriptWindowRejectsInvalidOffsets() {
        assertThrows(IllegalArgumentException::class.java) {
            SummaryTranscriptWindow(segmentId = 0L, startOffsetMs = 0L, endOffsetMs = 100L, text = "text")
        }
        assertThrows(IllegalArgumentException::class.java) {
            SummaryTranscriptWindow(segmentId = 1L, startOffsetMs = 100L, endOffsetMs = 100L, text = "text")
        }
    }
}

private class FakeSummaryProvider(
    private val result: SummaryResult,
) : SummaryProvider {
    override val providerId: String = "fake-summary"
    override val providerVersion: String = "1"
    var lastRequest: SummaryRequest? = null
        private set

    override suspend fun draft(request: SummaryRequest): SummaryResult {
        lastRequest = request
        return result
    }
}
