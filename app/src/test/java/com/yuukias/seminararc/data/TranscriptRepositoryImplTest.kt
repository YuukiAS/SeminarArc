package com.yuukias.seminararc.data

import com.yuukias.seminararc.data.local.dao.TranscriptDao
import com.yuukias.seminararc.data.local.entity.SummaryDraftEntity
import com.yuukias.seminararc.data.local.entity.TranscriptEntity
import com.yuukias.seminararc.data.local.entity.TranscriptSegmentEntity
import com.yuukias.seminararc.data.repository.TranscriptRepositoryImpl
import com.yuukias.seminararc.domain.model.SummaryDraftState
import com.yuukias.seminararc.domain.model.TranscriptLanguageHint
import com.yuukias.seminararc.domain.model.TranscriptSourceType
import com.yuukias.seminararc.domain.model.TranscriptState
import com.yuukias.seminararc.domain.repository.CreateTranscriptInput
import com.yuukias.seminararc.domain.repository.EditSummaryDraftInput
import com.yuukias.seminararc.domain.repository.SaveSummaryDraftInput
import com.yuukias.seminararc.domain.transcription.TranscriptionSegmentDraft
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TranscriptRepositoryImplTest {
    @Test
    fun createTranscriptAndSegmentsPersistsReadyTranscript() = runTest {
        val dao = FakeTranscriptDao()
        val repository = TranscriptRepositoryImpl(dao, FixedClock)
        val transcript = repository.createTranscript(
            CreateTranscriptInput(
                seminarId = 1L,
                recordingId = 2L,
                providerId = "fake-transcription",
                providerVersion = "1",
                languageHint = TranscriptLanguageHint.AUTO,
                sourceType = TranscriptSourceType.RECORDING,
                sourceAssetId = 3L,
            ),
        )

        repository.markTranscriptRunning(transcript.id)
        val segments = repository.saveTranscriptSegments(
            transcriptId = transcript.id,
            segments = listOf(
                TranscriptionSegmentDraft(
                    startOffsetMs = 1_000L,
                    endOffsetMs = 2_000L,
                    text = " First point ",
                    confidence = 0.9f,
                ),
            ),
        )
        val ready = repository.markTranscriptReady(transcript.id, TranscriptLanguageHint.EN)

        assertEquals(TranscriptState.READY, ready?.state)
        assertEquals(TranscriptLanguageHint.EN, ready?.languageHint)
        assertEquals("First point", segments.single().text)
        assertEquals(1_000L, segments.single().startOffsetMs)
        assertEquals(2L, segments.single().recordingId)
    }

    @Test
    fun replaceSegmentsDropsPreviousProviderSegments() = runTest {
        val dao = FakeTranscriptDao()
        val repository = TranscriptRepositoryImpl(dao, FixedClock)
        val transcript = repository.createTranscript(
            CreateTranscriptInput(
                seminarId = 1L,
                recordingId = 2L,
                providerId = "fake-transcription",
                providerVersion = "1",
                languageHint = TranscriptLanguageHint.AUTO,
                sourceType = TranscriptSourceType.RECORDING,
                sourceAssetId = 3L,
            ),
        )

        repository.saveTranscriptSegments(
            transcript.id,
            listOf(TranscriptionSegmentDraft(0L, 1_000L, "old")),
        )
        val latest = repository.saveTranscriptSegments(
            transcript.id,
            listOf(TranscriptionSegmentDraft(1_000L, 2_000L, "new")),
        )

        assertEquals(listOf("new"), latest.map { it.text })
        assertEquals(listOf("new"), repository.getSegments(transcript.id).map { it.text })
    }

    @Test
    fun editSegmentTextMarksSegmentEditedAndRefreshesTranscript() = runTest {
        val dao = FakeTranscriptDao()
        val repository = TranscriptRepositoryImpl(dao, FixedClock)
        val transcript = repository.createTranscript(
            CreateTranscriptInput(
                seminarId = 1L,
                recordingId = 2L,
                providerId = "fake-transcription",
                providerVersion = "1",
                languageHint = TranscriptLanguageHint.AUTO,
                sourceType = TranscriptSourceType.RECORDING,
                sourceAssetId = 3L,
            ),
        )
        val original = repository.saveTranscriptSegments(
            transcript.id,
            listOf(TranscriptionSegmentDraft(0L, 1_000L, "old")),
        ).single()

        val edited = repository.editSegmentText(original.id, " edited segment ")

        assertEquals("edited segment", edited?.text)
        assertEquals(true, edited?.isEdited)
        assertEquals(FixedClock.now(), edited?.updatedAt)
        assertEquals(FixedClock.now(), dao.getTranscript(transcript.id)?.updatedAt)
    }

    @Test
    fun editSegmentTextReturnsNullForMissingSegment() = runTest {
        val repository = TranscriptRepositoryImpl(FakeTranscriptDao(), FixedClock)

        assertNull(repository.editSegmentText(segmentId = 404L, text = "edited"))
    }

    @Test
    fun upsertSummaryDraftKeepsStableFingerprintIdentity() = runTest {
        val dao = FakeTranscriptDao()
        val repository = TranscriptRepositoryImpl(dao, FixedClock)

        val first = repository.upsertSummaryDraft(summaryInput(mainResults = "old"))
        val second = repository.upsertSummaryDraft(summaryInput(mainResults = "new"))

        assertEquals(first.id, second.id)
        assertEquals("new", second.mainResults)
        assertEquals(first.createdAt, second.createdAt)
        assertNull(second.errorMessage)
    }

    @Test
    fun editSummaryDraftStoresHumanDraftAndClearsError() = runTest {
        val dao = FakeTranscriptDao()
        val repository = TranscriptRepositoryImpl(dao, FixedClock)
        val original = repository.upsertSummaryDraft(
            summaryInput(mainResults = "old").copy(
                state = SummaryDraftState.FAILED,
                errorMessage = "provider unavailable",
            ),
        )

        val edited = repository.editSummaryDraft(
            EditSummaryDraftInput(
                draftId = original.id,
                backgroundContext = " background ",
                coreQuestion = " question ",
                methods = " methods ",
                mainResults = " revised results ",
                keyTakeaways = " takeaways ",
                unresolvedQuestions = " open questions ",
                followUpActions = " actions ",
                userNotes = " notes ",
            ),
        )

        assertEquals(SummaryDraftState.DRAFT, edited?.state)
        assertEquals("revised results", edited?.mainResults)
        assertEquals("background", edited?.backgroundContext)
        assertEquals(FixedClock.now(), edited?.updatedAt)
        assertNull(edited?.errorMessage)
    }

    private fun summaryInput(mainResults: String): SaveSummaryDraftInput {
        return SaveSummaryDraftInput(
            seminarId = 1L,
            providerId = "fake-summary",
            inputFingerprint = "fingerprint",
            state = SummaryDraftState.READY,
            backgroundContext = "background",
            coreQuestion = "question",
            methods = "methods",
            mainResults = mainResults,
            keyTakeaways = "takeaways",
            unresolvedQuestions = "questions",
            followUpActions = "actions",
            userNotes = "notes",
            provenanceJson = "{}",
            errorMessage = "",
        )
    }
}

private class FakeTranscriptDao : TranscriptDao {
    private val transcripts = linkedMapOf<Long, TranscriptEntity>()
    private val segments = linkedMapOf<Long, TranscriptSegmentEntity>()
    private val drafts = linkedMapOf<Long, SummaryDraftEntity>()
    private var nextTranscriptId = 1L
    private var nextSegmentId = 1L
    private var nextDraftId = 1L

    override fun observeTranscripts(seminarId: Long): Flow<List<TranscriptEntity>> {
        return flowOf(transcripts.values.filter { it.seminarId == seminarId })
    }

    override fun observeSegments(transcriptId: Long): Flow<List<TranscriptSegmentEntity>> {
        return flowOf(getSegmentsSync(transcriptId))
    }

    override fun observeSummaryDrafts(seminarId: Long): Flow<List<SummaryDraftEntity>> {
        return flowOf(drafts.values.filter { it.seminarId == seminarId })
    }

    override suspend fun getTranscript(transcriptId: Long): TranscriptEntity? = transcripts[transcriptId]

    override suspend fun getLatestTranscriptForRecording(
        seminarId: Long,
        recordingId: Long,
        providerId: String,
    ): TranscriptEntity? {
        return transcripts.values
            .filter { it.seminarId == seminarId && it.recordingId == recordingId && it.providerId == providerId }
            .maxWithOrNull(compareBy<TranscriptEntity> { it.updatedAt }.thenBy { it.id })
    }

    override suspend fun getSegments(transcriptId: Long): List<TranscriptSegmentEntity> = getSegmentsSync(transcriptId)

    override suspend fun getSegment(segmentId: Long): TranscriptSegmentEntity? = segments[segmentId]

    override suspend fun getSummaryDraft(draftId: Long): SummaryDraftEntity? = drafts[draftId]

    override suspend fun getSummaryDraftByFingerprint(
        seminarId: Long,
        inputFingerprint: String,
    ): SummaryDraftEntity? {
        return drafts.values.firstOrNull { it.seminarId == seminarId && it.inputFingerprint == inputFingerprint }
    }

    override suspend fun insertTranscript(entity: TranscriptEntity): Long {
        val id = nextTranscriptId++
        transcripts[id] = entity.copy(id = id)
        return id
    }

    override suspend fun updateTranscript(entity: TranscriptEntity) {
        transcripts[entity.id] = entity
    }

    override suspend fun insertSegments(entities: List<TranscriptSegmentEntity>): List<Long> {
        return entities.map { entity ->
            val id = nextSegmentId++
            segments[id] = entity.copy(id = id)
            id
        }
    }

    override suspend fun updateSegment(entity: TranscriptSegmentEntity) {
        segments[entity.id] = entity
    }

    override suspend fun updateSummaryDraft(entity: SummaryDraftEntity) {
        drafts[entity.id] = entity
    }

    override suspend fun deleteSegments(transcriptId: Long): Int {
        val ids = segments.values.filter { it.transcriptId == transcriptId }.map { it.id }
        ids.forEach { segments.remove(it) }
        return ids.size
    }

    override suspend fun upsertSummaryDraft(entity: SummaryDraftEntity): Long {
        val existing = drafts.values.firstOrNull {
            it.seminarId == entity.seminarId && it.inputFingerprint == entity.inputFingerprint
        }
        val id = existing?.id ?: nextDraftId++
        drafts[id] = entity.copy(id = id)
        return id
    }

    override suspend fun updateSegmentTextAndTranscript(
        segment: TranscriptSegmentEntity,
        transcript: TranscriptEntity,
    ) {
        updateSegment(segment)
        updateTranscript(transcript)
    }

    private fun getSegmentsSync(transcriptId: Long): List<TranscriptSegmentEntity> {
        return segments.values
            .filter { it.transcriptId == transcriptId }
            .sortedWith(compareBy<TranscriptSegmentEntity> { it.startOffsetMs }.thenBy { it.id })
    }
}

private val FixedClock = com.yuukias.seminararc.util.ClockProvider {
    Instant.parse("2026-09-08T00:00:00Z")
}
