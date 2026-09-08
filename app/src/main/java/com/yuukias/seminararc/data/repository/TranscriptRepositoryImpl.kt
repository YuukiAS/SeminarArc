package com.yuukias.seminararc.data.repository

import com.yuukias.seminararc.data.local.dao.TranscriptDao
import com.yuukias.seminararc.data.local.entity.SummaryDraftEntity
import com.yuukias.seminararc.data.local.entity.TranscriptEntity
import com.yuukias.seminararc.data.local.entity.TranscriptSegmentEntity
import com.yuukias.seminararc.domain.model.SummaryDraft
import com.yuukias.seminararc.domain.model.Transcript
import com.yuukias.seminararc.domain.model.TranscriptLanguageHint
import com.yuukias.seminararc.domain.model.TranscriptSegment
import com.yuukias.seminararc.domain.model.TranscriptState
import com.yuukias.seminararc.domain.repository.CreateTranscriptInput
import com.yuukias.seminararc.domain.repository.EditSummaryDraftInput
import com.yuukias.seminararc.domain.repository.SaveSummaryDraftInput
import com.yuukias.seminararc.domain.repository.TranscriptRepository
import com.yuukias.seminararc.domain.transcription.TranscriptionSegmentDraft
import com.yuukias.seminararc.util.ClockProvider
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TranscriptRepositoryImpl @Inject constructor(
    private val dao: TranscriptDao,
    private val clockProvider: ClockProvider,
) : TranscriptRepository {
    override fun observeTranscripts(seminarId: Long): Flow<List<Transcript>> {
        return dao.observeTranscripts(seminarId).map { rows -> rows.map { it.toDomain() } }
    }

    override fun observeSegments(transcriptId: Long): Flow<List<TranscriptSegment>> {
        return dao.observeSegments(transcriptId).map { rows -> rows.map { it.toDomain() } }
    }

    override fun observeSummaryDrafts(seminarId: Long): Flow<List<SummaryDraft>> {
        return dao.observeSummaryDrafts(seminarId).map { rows -> rows.map { it.toDomain() } }
    }

    override suspend fun getTranscript(transcriptId: Long): Transcript? {
        return dao.getTranscript(transcriptId)?.toDomain()
    }

    override suspend fun getLatestTranscriptForRecording(
        seminarId: Long,
        recordingId: Long,
        providerId: String,
    ): Transcript? {
        return dao.getLatestTranscriptForRecording(seminarId, recordingId, providerId)?.toDomain()
    }

    override suspend fun getSegments(transcriptId: Long): List<TranscriptSegment> {
        return dao.getSegments(transcriptId).map { it.toDomain() }
    }

    override suspend fun editSegmentText(
        segmentId: Long,
        text: String,
    ): TranscriptSegment? {
        val normalized = text.trim()
        require(normalized.isNotBlank()) { "Transcript segment text must not be blank." }
        val existing = dao.getSegment(segmentId) ?: return null
        val transcript = dao.getTranscript(existing.transcriptId) ?: return null
        val now = clockProvider.now()
        val updatedSegment = existing.copy(
            text = normalized,
            isEdited = true,
            updatedAt = now,
        )
        dao.updateSegmentTextAndTranscript(
            segment = updatedSegment,
            transcript = transcript.copy(updatedAt = now),
        )
        return updatedSegment.toDomain()
    }

    override suspend fun editSummaryDraft(input: EditSummaryDraftInput): SummaryDraft? {
        val existing = dao.getSummaryDraft(input.draftId) ?: return null
        val updated = existing.copy(
            state = com.yuukias.seminararc.domain.model.SummaryDraftState.DRAFT,
            backgroundContext = input.backgroundContext.trim(),
            coreQuestion = input.coreQuestion.trim(),
            methods = input.methods.trim(),
            mainResults = input.mainResults.trim(),
            keyTakeaways = input.keyTakeaways.trim(),
            unresolvedQuestions = input.unresolvedQuestions.trim(),
            followUpActions = input.followUpActions.trim(),
            userNotes = input.userNotes.trim(),
            errorMessage = null,
            updatedAt = clockProvider.now(),
        )
        dao.updateSummaryDraft(updated)
        return updated.toDomain()
    }

    override suspend fun createTranscript(input: CreateTranscriptInput): Transcript {
        require(input.seminarId > 0L) { "Seminar id must be positive." }
        input.recordingId?.let { require(it > 0L) { "Recording id must be positive." } }
        input.sourceAssetId?.let { require(it > 0L) { "Source asset id must be positive." } }
        require(input.providerId.isNotBlank()) { "Provider id must not be blank." }
        require(input.providerVersion.isNotBlank()) { "Provider version must not be blank." }
        val now = clockProvider.now()
        val id = dao.insertTranscript(
            TranscriptEntity(
                seminarId = input.seminarId,
                recordingId = input.recordingId,
                providerId = input.providerId,
                providerVersion = input.providerVersion,
                languageHint = input.languageHint,
                state = TranscriptState.QUEUED,
                sourceType = input.sourceType,
                sourceAssetId = input.sourceAssetId,
                errorMessage = null,
                createdAt = now,
                updatedAt = now,
            ),
        )
        return dao.getTranscript(id)?.toDomain() ?: error("Transcript $id was not readable after insert.")
    }

    override suspend fun markTranscriptRunning(transcriptId: Long): Transcript? {
        val existing = dao.getTranscript(transcriptId) ?: return null
        val updated = existing.copy(
            state = TranscriptState.RUNNING,
            errorMessage = null,
            updatedAt = clockProvider.now(),
        )
        dao.updateTranscript(updated)
        return updated.toDomain()
    }

    override suspend fun saveTranscriptSegments(
        transcriptId: Long,
        segments: List<TranscriptionSegmentDraft>,
    ): List<TranscriptSegment> {
        require(segments.isNotEmpty()) { "Transcript segments must not be empty." }
        val transcript = dao.getTranscript(transcriptId) ?: error("Transcript $transcriptId was not found.")
        val now = clockProvider.now()
        val entities = segments.map { segment ->
            TranscriptSegmentEntity(
                transcriptId = transcript.id,
                seminarId = transcript.seminarId,
                recordingId = transcript.recordingId,
                startOffsetMs = segment.startOffsetMs,
                endOffsetMs = segment.endOffsetMs,
                speakerLabel = segment.speakerLabel?.takeIf { it.isNotBlank() },
                language = segment.language?.takeIf { it.isNotBlank() },
                text = segment.text.trim(),
                confidence = segment.confidence,
                isEdited = false,
                providerSegmentId = segment.providerSegmentId,
                createdAt = now,
                updatedAt = now,
            )
        }
        dao.replaceSegments(transcriptId, entities)
        return dao.getSegments(transcriptId).map { it.toDomain() }
    }

    override suspend fun markTranscriptReady(
        transcriptId: Long,
        languageHint: TranscriptLanguageHint?,
    ): Transcript? {
        val existing = dao.getTranscript(transcriptId) ?: return null
        val updated = existing.copy(
            state = TranscriptState.READY,
            languageHint = languageHint ?: existing.languageHint,
            errorMessage = null,
            updatedAt = clockProvider.now(),
        )
        dao.updateTranscript(updated)
        return updated.toDomain()
    }

    override suspend fun markTranscriptFailed(
        transcriptId: Long,
        message: String,
    ): Transcript? {
        val existing = dao.getTranscript(transcriptId) ?: return null
        val updated = existing.copy(
            state = TranscriptState.FAILED,
            errorMessage = message.take(MAX_ERROR_LENGTH),
            updatedAt = clockProvider.now(),
        )
        dao.updateTranscript(updated)
        return updated.toDomain()
    }

    override suspend fun upsertSummaryDraft(input: SaveSummaryDraftInput): SummaryDraft {
        require(input.seminarId > 0L) { "Seminar id must be positive." }
        require(input.providerId.isNotBlank()) { "Provider id must not be blank." }
        require(input.inputFingerprint.isNotBlank()) { "Input fingerprint must not be blank." }
        val now = clockProvider.now()
        val existing = dao.getSummaryDraftByFingerprint(input.seminarId, input.inputFingerprint)
        val entity = SummaryDraftEntity(
            id = existing?.id ?: 0L,
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
            errorMessage = input.errorMessage?.takeIf { it.isNotBlank() }?.take(MAX_ERROR_LENGTH),
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        val id = dao.upsertSummaryDraft(entity)
        return (dao.getSummaryDraftByFingerprint(input.seminarId, input.inputFingerprint) ?: entity.copy(id = id)).toDomain()
    }

    private fun TranscriptEntity.toDomain(): Transcript {
        return Transcript(
            id = id,
            seminarId = seminarId,
            recordingId = recordingId,
            providerId = providerId,
            providerVersion = providerVersion,
            languageHint = languageHint,
            state = state,
            sourceType = sourceType,
            sourceAssetId = sourceAssetId,
            errorMessage = errorMessage,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }

    private fun TranscriptSegmentEntity.toDomain(): TranscriptSegment {
        return TranscriptSegment(
            id = id,
            transcriptId = transcriptId,
            seminarId = seminarId,
            recordingId = recordingId,
            startOffsetMs = startOffsetMs,
            endOffsetMs = endOffsetMs,
            speakerLabel = speakerLabel,
            language = language,
            text = text,
            confidence = confidence,
            isEdited = isEdited,
            providerSegmentId = providerSegmentId,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }

    private fun SummaryDraftEntity.toDomain(): SummaryDraft {
        return SummaryDraft(
            id = id,
            seminarId = seminarId,
            providerId = providerId,
            inputFingerprint = inputFingerprint,
            state = state,
            backgroundContext = backgroundContext,
            coreQuestion = coreQuestion,
            methods = methods,
            mainResults = mainResults,
            keyTakeaways = keyTakeaways,
            unresolvedQuestions = unresolvedQuestions,
            followUpActions = followUpActions,
            userNotes = userNotes,
            provenanceJson = provenanceJson,
            errorMessage = errorMessage,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }

    private companion object {
        const val MAX_ERROR_LENGTH = 1_000
    }
}
