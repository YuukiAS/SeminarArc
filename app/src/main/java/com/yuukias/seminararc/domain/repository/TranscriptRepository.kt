package com.yuukias.seminararc.domain.repository

import com.yuukias.seminararc.domain.model.SummaryDraft
import com.yuukias.seminararc.domain.model.SummaryDraftState
import com.yuukias.seminararc.domain.model.Transcript
import com.yuukias.seminararc.domain.model.TranscriptLanguageHint
import com.yuukias.seminararc.domain.model.TranscriptSegment
import com.yuukias.seminararc.domain.model.TranscriptSourceType
import com.yuukias.seminararc.domain.model.TranscriptState
import com.yuukias.seminararc.domain.transcription.TranscriptionSegmentDraft
import kotlinx.coroutines.flow.Flow

interface TranscriptRepository {
    fun observeTranscripts(seminarId: Long): Flow<List<Transcript>>

    fun observeSegments(transcriptId: Long): Flow<List<TranscriptSegment>>

    fun observeSummaryDrafts(seminarId: Long): Flow<List<SummaryDraft>>

    suspend fun getTranscript(transcriptId: Long): Transcript?

    suspend fun getLatestTranscriptForRecording(
        seminarId: Long,
        recordingId: Long,
        providerId: String,
    ): Transcript?

    suspend fun getSegments(transcriptId: Long): List<TranscriptSegment>

    suspend fun editSegmentText(
        segmentId: Long,
        text: String,
    ): TranscriptSegment?

    suspend fun createTranscript(input: CreateTranscriptInput): Transcript

    suspend fun markTranscriptRunning(transcriptId: Long): Transcript?

    suspend fun saveTranscriptSegments(
        transcriptId: Long,
        segments: List<TranscriptionSegmentDraft>,
    ): List<TranscriptSegment>

    suspend fun markTranscriptReady(
        transcriptId: Long,
        languageHint: TranscriptLanguageHint? = null,
    ): Transcript?

    suspend fun markTranscriptFailed(
        transcriptId: Long,
        message: String,
    ): Transcript?

    suspend fun upsertSummaryDraft(input: SaveSummaryDraftInput): SummaryDraft
}

data class CreateTranscriptInput(
    val seminarId: Long,
    val recordingId: Long?,
    val providerId: String,
    val providerVersion: String,
    val languageHint: TranscriptLanguageHint,
    val sourceType: TranscriptSourceType,
    val sourceAssetId: Long?,
)

data class SaveSummaryDraftInput(
    val seminarId: Long,
    val providerId: String,
    val inputFingerprint: String,
    val state: SummaryDraftState,
    val backgroundContext: String,
    val coreQuestion: String,
    val methods: String,
    val mainResults: String,
    val keyTakeaways: String,
    val unresolvedQuestions: String,
    val followUpActions: String,
    val userNotes: String,
    val provenanceJson: String,
    val errorMessage: String?,
)
