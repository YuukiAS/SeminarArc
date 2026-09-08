package com.yuukias.seminararc.domain

import com.yuukias.seminararc.domain.model.AbstractAttachment
import com.yuukias.seminararc.domain.model.ActiveSeminarSessionState
import com.yuukias.seminararc.domain.model.BriefKeySlide
import com.yuukias.seminararc.domain.model.BriefReference
import com.yuukias.seminararc.domain.model.CompleteSeminarResult
import com.yuukias.seminararc.domain.model.ReferenceCandidate
import com.yuukias.seminararc.domain.model.ReferenceCandidateSource
import com.yuukias.seminararc.domain.model.ReferenceCandidateStatus
import com.yuukias.seminararc.domain.model.ReferenceConfidenceBand
import com.yuukias.seminararc.domain.model.ReferenceEvidence
import com.yuukias.seminararc.domain.model.ReferenceLookupAttempt
import com.yuukias.seminararc.domain.model.ReferenceLookupSummary
import com.yuukias.seminararc.domain.model.ReferenceQueryPreview
import com.yuukias.seminararc.domain.model.SeminarAsset
import com.yuukias.seminararc.domain.model.SeminarAssetType
import com.yuukias.seminararc.domain.model.SeminarBrief
import com.yuukias.seminararc.domain.model.SeminarBriefBundle
import com.yuukias.seminararc.domain.model.SeminarDetail
import com.yuukias.seminararc.domain.model.SeminarDraftInput
import com.yuukias.seminararc.domain.model.SeminarEditorData
import com.yuukias.seminararc.domain.model.SeminarListFilter
import com.yuukias.seminararc.domain.model.SeminarStatus
import com.yuukias.seminararc.domain.model.SeminarSummary
import com.yuukias.seminararc.domain.model.StartSeminarSessionResult
import com.yuukias.seminararc.domain.model.SummaryDraft
import com.yuukias.seminararc.domain.model.SummaryDraftState
import com.yuukias.seminararc.domain.model.TranscriptSegment
import com.yuukias.seminararc.domain.repository.CreateReferenceEvidenceInput
import com.yuukias.seminararc.domain.repository.ReferenceRepository
import com.yuukias.seminararc.domain.repository.SaveSeminarBriefInput
import com.yuukias.seminararc.domain.repository.SaveSummaryDraftInput
import com.yuukias.seminararc.domain.repository.SeminarRepository
import com.yuukias.seminararc.domain.repository.TranscriptRepository
import com.yuukias.seminararc.domain.summary.SummaryDraftContent
import com.yuukias.seminararc.domain.summary.SummaryProvider
import com.yuukias.seminararc.domain.summary.SummaryRequest
import com.yuukias.seminararc.domain.summary.SummaryResult
import com.yuukias.seminararc.domain.usecase.DraftSummaryForSeminarUseCase
import com.yuukias.seminararc.domain.usecase.DraftSummaryInput
import com.yuukias.seminararc.domain.usecase.DraftSummaryResult
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DraftSummaryForSeminarUseCaseTest {
    @Test
    fun draftsSummaryFromSelectedTranscriptWindowsWithoutSavingBrief() = runTest {
        val transcriptRepository = SummaryUseCaseFakeTranscriptRepository(
            segments = listOf(segment(7L, text = "selected"), segment(8L, text = "ignored")),
        )
        val referenceRepository = SummaryUseCaseFakeReferenceRepository(briefBundle = briefBundle())
        val provider = SummaryUseCaseFakeProvider(
            SummaryResult.Drafted(
                SummaryDraftContent(
                    backgroundContext = "background",
                    coreQuestion = "question",
                    methods = "methods",
                    mainResults = "results",
                    keyTakeaways = "takeaways",
                    unresolvedQuestions = "questions",
                    followUpActions = "actions",
                    userNotes = "manual notes",
                    provenanceJson = """{"provider":"fake"}""",
                ),
            ),
        )
        val useCase = DraftSummaryForSeminarUseCase(
            seminarRepository = SummaryUseCaseFakeSeminarRepository(detail()),
            transcriptRepository = transcriptRepository,
            referenceRepository = referenceRepository,
            summaryProvider = provider,
        )

        val result = useCase(
            DraftSummaryInput(
                seminarId = 1L,
                transcriptId = 2L,
                selectedSegmentIds = listOf(7L),
                userNotes = "manual notes",
            ),
        ) as DraftSummaryResult.Drafted

        assertEquals(SummaryDraftState.READY, result.draft.state)
        assertEquals("results", result.draft.mainResults)
        assertEquals(listOf("selected"), provider.lastRequest?.transcriptWindows?.map { it.text })
        assertEquals(listOf("Reference title"), provider.lastRequest?.referenceTitles)
        assertEquals(listOf("Key caption"), provider.lastRequest?.keySlideCaptions)
        assertEquals(0, referenceRepository.saveBriefCalls)
    }

    @Test
    fun requiresUserSelectedTranscriptSegments() = runTest {
        val provider = SummaryUseCaseFakeProvider(
            SummaryResult.Failed("should not run"),
        )
        val useCase = DraftSummaryForSeminarUseCase(
            seminarRepository = SummaryUseCaseFakeSeminarRepository(detail()),
            transcriptRepository = SummaryUseCaseFakeTranscriptRepository(listOf(segment(7L))),
            referenceRepository = SummaryUseCaseFakeReferenceRepository(null),
            summaryProvider = provider,
        )

        val result = useCase(DraftSummaryInput(seminarId = 1L, transcriptId = 2L, selectedSegmentIds = emptyList()))

        assertEquals(
            DraftSummaryResult.Failed(
                "Select transcript segments before drafting a summary.",
                isRetryable = false,
            ),
            result,
        )
        assertNull(provider.lastRequest)
    }

    @Test
    fun providerFailurePersistsFailedDraftWithManualNotes() = runTest {
        val transcriptRepository = SummaryUseCaseFakeTranscriptRepository(listOf(segment(7L)))
        val useCase = DraftSummaryForSeminarUseCase(
            seminarRepository = SummaryUseCaseFakeSeminarRepository(detail()),
            transcriptRepository = transcriptRepository,
            referenceRepository = SummaryUseCaseFakeReferenceRepository(briefBundle()),
            summaryProvider = SummaryUseCaseFakeProvider(SummaryResult.Failed("summary unavailable", isRetryable = false)),
        )

        val result = useCase(DraftSummaryInput(seminarId = 1L, transcriptId = 2L, selectedSegmentIds = listOf(7L)))

        assertEquals(DraftSummaryResult.Failed("summary unavailable", isRetryable = false), result)
        assertEquals(SummaryDraftState.FAILED, transcriptRepository.savedDrafts.single().state)
        assertEquals("existing notes", transcriptRepository.savedDrafts.single().userNotes)
        assertEquals("summary unavailable", transcriptRepository.savedDrafts.single().errorMessage)
    }
}

private class SummaryUseCaseFakeProvider(
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

private class SummaryUseCaseFakeSeminarRepository(
    private val detail: SeminarDetail?,
) : SeminarRepository {
    override fun observeSeminars(filter: SeminarListFilter, query: String): Flow<List<SeminarSummary>> = flowOf(emptyList())
    override fun observeSeminarDetail(seminarId: Long): Flow<SeminarDetail?> = flowOf(detail?.takeIf { it.id == seminarId })
    override suspend fun getSeminarEditorData(seminarId: Long): SeminarEditorData? = null
    override suspend fun saveSeminar(input: SeminarDraftInput): Long = error("Not used.")
    override suspend fun getActiveSeminarSessionState(): ActiveSeminarSessionState = error("Not used.")
    override suspend fun startSeminarSession(seminarId: Long): StartSeminarSessionResult = error("Not used.")
    override suspend fun completeActiveSeminar(seminarId: Long): CompleteSeminarResult = error("Not used.")
    override suspend fun importAbstractPdf(seminarId: Long, sourceUri: String): AbstractAttachment = error("Not used.")
    override suspend fun removeAbstractPdf(seminarId: Long) = Unit
    override suspend fun setFavorite(seminarId: Long, isFavorite: Boolean) = Unit
    override suspend fun setRating(seminarId: Long, rating: Int?) = Unit
    override suspend fun deleteSeminar(seminarId: Long) = Unit
}

private class SummaryUseCaseFakeTranscriptRepository(
    private val segments: List<TranscriptSegment>,
) : TranscriptRepository {
    val savedDrafts = mutableListOf<SummaryDraft>()
    private var nextDraftId = 1L

    override fun observeTranscripts(seminarId: Long) = flowOf(emptyList<com.yuukias.seminararc.domain.model.Transcript>())
    override fun observeSegments(transcriptId: Long): Flow<List<TranscriptSegment>> = flowOf(segments)
    override fun observeSummaryDrafts(seminarId: Long): Flow<List<SummaryDraft>> = flowOf(savedDrafts)
    override suspend fun getTranscript(transcriptId: Long) = null
    override suspend fun getLatestTranscriptForRecording(seminarId: Long, recordingId: Long, providerId: String) = null
    override suspend fun getSegments(transcriptId: Long): List<TranscriptSegment> = segments.filter { it.transcriptId == transcriptId }
    override suspend fun createTranscript(input: com.yuukias.seminararc.domain.repository.CreateTranscriptInput) = error("Not used.")
    override suspend fun markTranscriptRunning(transcriptId: Long) = null
    override suspend fun saveTranscriptSegments(
        transcriptId: Long,
        segments: List<com.yuukias.seminararc.domain.transcription.TranscriptionSegmentDraft>,
    ) = error("Not used.")
    override suspend fun markTranscriptReady(
        transcriptId: Long,
        languageHint: com.yuukias.seminararc.domain.model.TranscriptLanguageHint?,
    ) = null
    override suspend fun markTranscriptFailed(transcriptId: Long, message: String) = null
    override suspend fun upsertSummaryDraft(input: SaveSummaryDraftInput): SummaryDraft {
        val existing = savedDrafts.firstOrNull { it.inputFingerprint == input.inputFingerprint }
        val draft = SummaryDraft(
            id = existing?.id ?: nextDraftId++,
            seminarId = input.seminarId,
            providerId = input.providerId,
            inputFingerprint = input.inputFingerprint,
            state = input.state,
            backgroundContext = input.backgroundContext,
            coreQuestion = input.coreQuestion,
            methods = input.methods,
            mainResults = input.mainResults,
            keyTakeaways = input.keyTakeaways,
            unresolvedQuestions = input.unresolvedQuestions,
            followUpActions = input.followUpActions,
            userNotes = input.userNotes,
            provenanceJson = input.provenanceJson,
            errorMessage = input.errorMessage,
            createdAt = existing?.createdAt ?: SUMMARY_NOW,
            updatedAt = SUMMARY_NOW,
        )
        savedDrafts.removeAll { it.id == draft.id }
        savedDrafts += draft
        return draft
    }
}

private class SummaryUseCaseFakeReferenceRepository(
    private val briefBundle: SeminarBriefBundle?,
) : ReferenceRepository {
    var saveBriefCalls = 0
        private set

    override fun observeEvidence(seminarId: Long): Flow<List<ReferenceEvidence>> = flowOf(emptyList())
    override fun observeCandidates(seminarId: Long): Flow<List<ReferenceCandidate>> = flowOf(emptyList())
    override fun observeAttempts(seminarId: Long): Flow<List<ReferenceLookupAttempt>> = flowOf(emptyList())
    override fun observeBrief(seminarId: Long): Flow<SeminarBrief?> = flowOf(briefBundle?.brief)
    override suspend fun createEvidence(input: CreateReferenceEvidenceInput): ReferenceEvidence = error("Not used.")
    override suspend fun buildQueryPreview(seminarId: Long, evidenceIds: List<Long>, manualText: String): ReferenceQueryPreview? = null
    override suspend fun runLookup(preview: ReferenceQueryPreview): ReferenceLookupSummary = error("Not used.")
    override suspend fun cancelLookup(requestFingerprint: String): ReferenceLookupAttempt? = null
    override suspend fun reviewCandidate(candidateId: Long, status: ReferenceCandidateStatus): ReferenceCandidate? = null
    override suspend fun getSourcesForCandidate(candidateId: Long): List<ReferenceCandidateSource> = emptyList()
    override suspend fun getOrCreateBrief(seminarId: Long): SeminarBrief = error("Not used.")
    override suspend fun saveBrief(input: SaveSeminarBriefInput): SeminarBrief? {
        saveBriefCalls += 1
        return null
    }
    override suspend fun setBriefReference(briefId: Long, candidateId: Long, included: Boolean, note: String?): Boolean = false
    override suspend fun setBriefKeySlide(briefId: Long, assetId: Long, included: Boolean, caption: String?): Boolean = false
    override suspend fun getBriefBundle(seminarId: Long): SeminarBriefBundle? = briefBundle?.takeIf { it.brief.seminarId == seminarId }
}

private fun detail(): SeminarDetail {
    return SeminarDetail(
        id = 1L,
        title = "Seminar",
        speaker = "Speaker",
        affiliation = "Lab",
        scheduledAt = null,
        location = null,
        abstractText = "Abstract",
        abstractAttachment = null,
        status = SeminarStatus.COMPLETED,
        sessionStartedAt = SUMMARY_NOW,
        sessionEndedAt = SUMMARY_NOW,
        rating = null,
        isFavorite = false,
        photoCount = 0,
        clipCount = 0,
        recordingDurationMs = 60_000L,
        timelinePreview = emptyList(),
    )
}

private fun segment(id: Long, text: String = "segment"): TranscriptSegment {
    return TranscriptSegment(
        id = id,
        transcriptId = 2L,
        seminarId = 1L,
        recordingId = 3L,
        startOffsetMs = id * 1_000L,
        endOffsetMs = id * 1_000L + 500L,
        speakerLabel = null,
        language = null,
        text = text,
        confidence = null,
        isEdited = false,
        providerSegmentId = null,
        createdAt = SUMMARY_NOW,
        updatedAt = SUMMARY_NOW,
    )
}

private fun briefBundle(): SeminarBriefBundle {
    val brief = SeminarBrief(
        id = 1L,
        seminarId = 1L,
        backgroundContext = "",
        coreQuestion = "",
        methods = "",
        mainResults = "",
        keyTakeaways = "",
        unresolvedQuestions = "",
        followUpActions = "",
        userNotes = "existing notes",
        createdAt = SUMMARY_NOW,
        updatedAt = SUMMARY_NOW,
    )
    val candidate = ReferenceCandidate(
        id = 1L,
        seminarId = 1L,
        canonicalDoi = null,
        normalizedDoi = null,
        title = "Reference title",
        normalizedTitle = "reference title",
        authorsJson = "[]",
        publicationYear = null,
        venue = null,
        sourceTitle = null,
        publicationType = null,
        landingPageUrl = null,
        openAccessUrl = null,
        licenseUrl = null,
        matcherVersion = "reference-match-v1",
        matchScore = 90,
        confidenceBand = ReferenceConfidenceBand.HIGH_CONFIDENCE,
        matchReasonsJson = "[]",
        status = ReferenceCandidateStatus.CONFIRMED,
        evidenceFingerprint = "fingerprint",
        lookupAttemptId = null,
        createdAt = SUMMARY_NOW,
        updatedAt = SUMMARY_NOW,
        reviewedAt = SUMMARY_NOW,
    )
    val slide = SeminarAsset(
        id = 4L,
        seminarId = 1L,
        type = SeminarAssetType.PHOTO_ORIGINAL,
        originAssetId = null,
        sourceTimelineEventId = null,
        sourceRecordingId = null,
        sourceClipId = null,
        relativePath = "seminars/1/photos/key.jpg",
        mimeType = "image/jpeg",
        displayName = "key.jpg",
        createdAt = SUMMARY_NOW,
        updatedAt = SUMMARY_NOW,
    )
    return SeminarBriefBundle(
        brief = brief,
        references = listOf(candidate to BriefReference(brief.id, candidate.id, 0, null)),
        keySlides = listOf(slide to BriefKeySlide(brief.id, slide.id, 0, "Key caption")),
    )
}

private val SUMMARY_NOW: Instant = Instant.parse("2026-09-08T13:00:00Z")
