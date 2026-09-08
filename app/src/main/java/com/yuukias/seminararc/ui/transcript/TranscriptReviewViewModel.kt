package com.yuukias.seminararc.ui.transcript

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.yuukias.seminararc.domain.model.ProcessingJobType
import com.yuukias.seminararc.domain.model.RecordingState
import com.yuukias.seminararc.domain.model.TranscriptLanguageHint
import com.yuukias.seminararc.domain.model.TranscriptSourceType
import com.yuukias.seminararc.domain.model.TranscriptState
import com.yuukias.seminararc.domain.repository.CreateTranscriptInput
import com.yuukias.seminararc.domain.repository.EditSummaryDraftInput
import com.yuukias.seminararc.domain.repository.ReferenceRepository
import com.yuukias.seminararc.domain.repository.RecordingRepository
import com.yuukias.seminararc.domain.repository.ReconstructionRepository
import com.yuukias.seminararc.domain.repository.SaveSeminarBriefInput
import com.yuukias.seminararc.domain.repository.SeminarRepository
import com.yuukias.seminararc.domain.repository.TranscriptRepository
import com.yuukias.seminararc.domain.transcription.TranscriptionSegmentDraft
import com.yuukias.seminararc.domain.usecase.BuildTranscriptTimelineWindowsUseCase
import com.yuukias.seminararc.domain.usecase.TranscriptTimelineWindowInput
import com.yuukias.seminararc.domain.usecase.TranscriptTimelineWindowResult
import com.yuukias.seminararc.media.processing.ProcessingWorkScheduler
import com.yuukias.seminararc.ui.navigation.TranscriptReviewRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class TranscriptReviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val seminarRepository: SeminarRepository,
    private val recordingRepository: RecordingRepository,
    private val reconstructionRepository: ReconstructionRepository,
    private val referenceRepository: ReferenceRepository,
    private val transcriptRepository: TranscriptRepository,
    private val buildTranscriptTimelineWindows: BuildTranscriptTimelineWindowsUseCase,
    private val processingWorkScheduler: ProcessingWorkScheduler,
) : ViewModel() {
    private val seminarId: Long = savedStateHandle["seminarId"]
        ?: savedStateHandle.toRoute<TranscriptReviewRoute>().seminarId

    private val selectedTranscriptId = MutableStateFlow<Long?>(null)
    private val editedSegments = MutableStateFlow<Map<Long, String>>(emptyMap())
    private val editedSummaryDrafts = MutableStateFlow<Map<Long, SummaryDraftEditDraft>>(emptyMap())
    private val manualTranscriptDraft = MutableStateFlow("")

    private val _events = MutableSharedFlow<TranscriptReviewEvent>(replay = 0)
    val events: SharedFlow<TranscriptReviewEvent> = _events.asSharedFlow()

    private val dataSnapshot = combine(
        seminarRepository.observeSeminarDetail(seminarId),
        transcriptRepository.observeTranscripts(seminarId),
        transcriptRepository.observeSummaryDrafts(seminarId),
        reconstructionRepository.observeJobsForSeminar(seminarId),
    ) { detail, transcripts, drafts, jobs ->
        TranscriptReviewData(
            detail = detail,
            transcripts = transcripts,
            summaryDrafts = drafts,
            processingJobs = jobs,
        )
    }

    val uiState: StateFlow<TranscriptReviewUiState> = combine(
        dataSnapshot,
        selectedTranscriptId,
        editedSegments,
        editedSummaryDrafts,
        manualTranscriptDraft,
    ) { data, selectedId, segmentDrafts, summaryDraftEdits, manualDraft ->
        TranscriptReviewSnapshot(
            detail = data.detail,
            transcripts = data.transcripts,
            summaryDrafts = data.summaryDrafts,
            processingJobs = data.processingJobs,
            selectedTranscriptId = selectedId,
            segmentDrafts = segmentDrafts,
            summaryDraftEdits = summaryDraftEdits,
            manualTranscriptDraft = manualDraft,
        )
    }
        .mapLatest { snapshot -> snapshot.toUiState() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TranscriptReviewUiState.Loading)

    fun onTranscriptSelected(transcriptId: Long) {
        selectedTranscriptId.value = transcriptId
    }

    fun onSegmentDraftChanged(segmentId: Long, text: String) {
        editedSegments.value = editedSegments.value + (segmentId to text)
    }

    fun onManualTranscriptDraftChanged(text: String) {
        manualTranscriptDraft.value = text
    }

    fun onSummaryDraftFieldChanged(
        draftId: Long,
        field: SummaryDraftField,
        text: String,
    ) {
        val readyState = uiState.value as? TranscriptReviewUiState.Ready
        val current = editedSummaryDrafts.value[draftId]
            ?: readyState?.summaryDrafts?.firstOrNull { draft -> draft.id == draftId }?.toEditDraft()
            ?: return
        editedSummaryDrafts.value = editedSummaryDrafts.value + (draftId to current.withField(field, text))
    }

    fun onSaveSummaryDraftClicked(draftId: Long) {
        viewModelScope.launch {
            val draft = editedSummaryDrafts.value[draftId]
            if (draft == null) {
                _events.emit(TranscriptReviewEvent.ShowMessage("Summary draft has no local changes."))
                return@launch
            }
            val edited = transcriptRepository.editSummaryDraft(draft.toEditInput(draftId))
            if (edited == null) {
                _events.emit(TranscriptReviewEvent.ShowMessage("Summary draft could not be updated."))
            } else {
                editedSummaryDrafts.value = editedSummaryDrafts.value - draftId
                _events.emit(TranscriptReviewEvent.ShowMessage("Summary draft updated."))
            }
        }
    }

    fun onApplySummaryDraftToBriefClicked(draftId: Long) {
        viewModelScope.launch {
            val readyState = uiState.value as? TranscriptReviewUiState.Ready
            val draft = editedSummaryDrafts.value[draftId]
                ?: readyState?.summaryDrafts?.firstOrNull { item -> item.id == draftId }?.toEditDraft()
            if (draft == null) {
                _events.emit(TranscriptReviewEvent.ShowMessage("Summary draft could not be applied."))
                return@launch
            }
            val saved = referenceRepository.saveBrief(draft.toBriefInput(seminarId))
            val message = if (saved == null) {
                "Seminar Brief could not be updated from this draft."
            } else {
                "Summary draft applied to Seminar Brief."
            }
            _events.emit(TranscriptReviewEvent.ShowMessage(message))
        }
    }

    fun onImportManualTranscriptClicked() {
        viewModelScope.launch {
            val segments = manualTranscriptDraft.value.toManualSegments()
            if (segments.isEmpty()) {
                _events.emit(TranscriptReviewEvent.ShowMessage("Paste transcript text before importing."))
                return@launch
            }
            val imported = runCatching {
                val transcript = transcriptRepository.createTranscript(
                    CreateTranscriptInput(
                        seminarId = seminarId,
                        recordingId = null,
                        providerId = MANUAL_TRANSCRIPT_PROVIDER_ID,
                        providerVersion = MANUAL_TRANSCRIPT_PROVIDER_VERSION,
                        languageHint = TranscriptLanguageHint.AUTO,
                        sourceType = TranscriptSourceType.MANUAL,
                        sourceAssetId = null,
                    ),
                )
                transcriptRepository.saveTranscriptSegments(transcript.id, segments)
                transcriptRepository.markTranscriptReady(transcript.id, TranscriptLanguageHint.AUTO)
                transcript
            }.getOrNull()
            if (imported == null) {
                _events.emit(TranscriptReviewEvent.ShowMessage("Manual transcript could not be imported."))
            } else {
                manualTranscriptDraft.value = ""
                selectedTranscriptId.value = imported.id
                _events.emit(TranscriptReviewEvent.ShowMessage("Manual transcript imported."))
            }
        }
    }

    fun onSaveSegmentClicked(segmentId: Long) {
        viewModelScope.launch {
            val text = editedSegments.value[segmentId]
            if (text.isNullOrBlank()) {
                _events.emit(TranscriptReviewEvent.ShowMessage("Transcript segment text cannot be blank."))
                return@launch
            }
            val edited = transcriptRepository.editSegmentText(segmentId, text)
            if (edited == null) {
                _events.emit(TranscriptReviewEvent.ShowMessage("Transcript segment could not be updated."))
            } else {
                editedSegments.value = editedSegments.value - segmentId
                _events.emit(TranscriptReviewEvent.ShowMessage("Transcript segment updated."))
            }
        }
    }

    fun onDraftSummaryClicked() {
        viewModelScope.launch {
            val readyState = uiState.value as? TranscriptReviewUiState.Ready
            val transcript = readyState?.selectedTranscript
            val segmentIds = readyState?.segments.orEmpty().map { it.id }
            if (transcript == null || segmentIds.isEmpty()) {
                _events.emit(TranscriptReviewEvent.ShowMessage("Select a transcript with segments before drafting a summary."))
                return@launch
            }
            val job = processingWorkScheduler.enqueueSummaryDraft(
                seminarId = seminarId,
                transcriptId = transcript.id,
                selectedSegmentIds = segmentIds,
            )
            val message = if (job == null) {
                "Summary draft could not be queued for this transcript."
            } else {
                "Summary draft job queued. Configure a live provider to produce editable draft prose."
            }
            _events.emit(TranscriptReviewEvent.ShowMessage(message))
        }
    }

    fun onRunTranscriptionClicked() {
        viewModelScope.launch {
            val recording = recordingRepository.observeRecordingsForSeminar(seminarId)
                .first()
                .filter { it.state == RecordingState.COMPLETED }
                .maxByOrNull { it.endedAt ?: it.startedAt }
            if (recording == null) {
                _events.emit(TranscriptReviewEvent.ShowMessage("No completed recording is available for transcription."))
                return@launch
            }
            val job = processingWorkScheduler.enqueueTranscription(recording.id, TranscriptLanguageHint.AUTO)
            val message = if (job == null) {
                "Transcription could not be queued for this recording."
            } else {
                "Transcription job queued. Configure a live provider to produce transcript segments."
            }
            _events.emit(TranscriptReviewEvent.ShowMessage(message))
        }
    }

    fun onRetryJob(jobId: Long) {
        viewModelScope.launch {
            val job = processingWorkScheduler.retry(jobId)
            val message = if (job == null) {
                "Processing job could not be retried."
            } else {
                "Processing retry queued."
            }
            _events.emit(TranscriptReviewEvent.ShowMessage(message))
        }
    }

    fun onCancelJob(jobId: Long) {
        viewModelScope.launch {
            processingWorkScheduler.cancel(jobId)
            _events.emit(TranscriptReviewEvent.ShowMessage("Processing job cancelled."))
        }
    }

    private suspend fun TranscriptReviewSnapshot.toUiState(): TranscriptReviewUiState {
        val currentDetail = detail ?: return TranscriptReviewUiState.Missing(seminarId)
        val orderedTranscripts = transcripts.sortedWith(
            compareByDescending<com.yuukias.seminararc.domain.model.Transcript> { it.updatedAt }
                .thenByDescending { it.id },
        )
        val selected = selectedTranscriptId
            ?.let { id -> orderedTranscripts.firstOrNull { transcript -> transcript.id == id } }
            ?: orderedTranscripts.firstOrNull { transcript -> transcript.state == TranscriptState.READY }
            ?: orderedTranscripts.firstOrNull()
        val segments = selected?.let { transcript ->
            transcriptRepository.getSegments(transcript.id)
        }.orEmpty()
        val windows = selected
            ?.takeIf { transcript -> transcript.state == TranscriptState.READY }
            ?.let { transcript ->
                when (
                    val result = buildTranscriptTimelineWindows(
                        TranscriptTimelineWindowInput(
                            seminarId = seminarId,
                            transcriptId = transcript.id,
                        ),
                    )
                ) {
                    is TranscriptTimelineWindowResult.Ready -> result.windows
                    is TranscriptTimelineWindowResult.Failed -> emptyList()
                }
            }.orEmpty()
        return TranscriptReviewUiState.Ready(
            detail = currentDetail,
            transcripts = orderedTranscripts,
            selectedTranscript = selected,
            segments = segments,
            segmentDrafts = segments.associate { segment ->
                segment.id to (segmentDrafts[segment.id] ?: segment.text)
            },
            manualTranscriptDraft = manualTranscriptDraft,
            summaryDraftEdits = summaryDrafts.associate { draft ->
                draft.id to (summaryDraftEdits[draft.id] ?: draft.toEditDraft())
            },
            timelineWindows = windows,
            summaryDrafts = summaryDrafts.sortedWith(compareByDescending { draft -> draft.updatedAt }),
            processingJobs = processingJobs
                .filter { job -> job.type in transcriptProcessingTypes }
                .sortedWith(
                    compareByDescending<com.yuukias.seminararc.domain.model.ProcessingJob> { job ->
                        job.startedAt ?: job.completedAt ?: job.createdAt
                    }.thenByDescending { job -> job.id },
                ),
        )
    }

    private companion object {
        val transcriptProcessingTypes = setOf(
            ProcessingJobType.TRANSCRIPTION,
            ProcessingJobType.SUMMARY_DRAFT,
        )
        const val MANUAL_TRANSCRIPT_PROVIDER_ID = "manual-transcript"
        const val MANUAL_TRANSCRIPT_PROVIDER_VERSION = "0.4-local"
    }
}

private data class TranscriptReviewSnapshot(
    val detail: com.yuukias.seminararc.domain.model.SeminarDetail?,
    val transcripts: List<com.yuukias.seminararc.domain.model.Transcript>,
    val summaryDrafts: List<com.yuukias.seminararc.domain.model.SummaryDraft>,
    val processingJobs: List<com.yuukias.seminararc.domain.model.ProcessingJob>,
    val selectedTranscriptId: Long?,
    val segmentDrafts: Map<Long, String>,
    val summaryDraftEdits: Map<Long, SummaryDraftEditDraft>,
    val manualTranscriptDraft: String,
)

private data class TranscriptReviewData(
    val detail: com.yuukias.seminararc.domain.model.SeminarDetail?,
    val transcripts: List<com.yuukias.seminararc.domain.model.Transcript>,
    val summaryDrafts: List<com.yuukias.seminararc.domain.model.SummaryDraft>,
    val processingJobs: List<com.yuukias.seminararc.domain.model.ProcessingJob>,
)

private fun String.toManualSegments(): List<TranscriptionSegmentDraft> {
    return lines()
        .map { line -> line.trim() }
        .filter { line -> line.isNotBlank() }
        .mapIndexed { index, line ->
            val start = index * MANUAL_SEGMENT_DURATION_MS
            TranscriptionSegmentDraft(
                startOffsetMs = start,
                endOffsetMs = start + MANUAL_SEGMENT_DURATION_MS,
                text = line,
                confidence = null,
                providerSegmentId = "manual-${index + 1}",
            )
        }
}

private const val MANUAL_SEGMENT_DURATION_MS = 60_000L

private fun com.yuukias.seminararc.domain.model.SummaryDraft.toEditDraft(): SummaryDraftEditDraft {
    return SummaryDraftEditDraft(
        backgroundContext = backgroundContext,
        coreQuestion = coreQuestion,
        methods = methods,
        mainResults = mainResults,
        keyTakeaways = keyTakeaways,
        unresolvedQuestions = unresolvedQuestions,
        followUpActions = followUpActions,
        userNotes = userNotes,
    )
}

private fun SummaryDraftEditDraft.withField(
    field: SummaryDraftField,
    text: String,
): SummaryDraftEditDraft {
    return when (field) {
        SummaryDraftField.BACKGROUND_CONTEXT -> copy(backgroundContext = text)
        SummaryDraftField.CORE_QUESTION -> copy(coreQuestion = text)
        SummaryDraftField.METHODS -> copy(methods = text)
        SummaryDraftField.MAIN_RESULTS -> copy(mainResults = text)
        SummaryDraftField.KEY_TAKEAWAYS -> copy(keyTakeaways = text)
        SummaryDraftField.UNRESOLVED_QUESTIONS -> copy(unresolvedQuestions = text)
        SummaryDraftField.FOLLOW_UP_ACTIONS -> copy(followUpActions = text)
        SummaryDraftField.USER_NOTES -> copy(userNotes = text)
    }
}

private fun SummaryDraftEditDraft.toEditInput(draftId: Long): EditSummaryDraftInput {
    return EditSummaryDraftInput(
        draftId = draftId,
        backgroundContext = backgroundContext,
        coreQuestion = coreQuestion,
        methods = methods,
        mainResults = mainResults,
        keyTakeaways = keyTakeaways,
        unresolvedQuestions = unresolvedQuestions,
        followUpActions = followUpActions,
        userNotes = userNotes,
    )
}

private fun SummaryDraftEditDraft.toBriefInput(seminarId: Long): SaveSeminarBriefInput {
    return SaveSeminarBriefInput(
        seminarId = seminarId,
        backgroundContext = backgroundContext,
        coreQuestion = coreQuestion,
        methods = methods,
        mainResults = mainResults,
        keyTakeaways = keyTakeaways,
        unresolvedQuestions = unresolvedQuestions,
        followUpActions = followUpActions,
        userNotes = userNotes,
    )
}
