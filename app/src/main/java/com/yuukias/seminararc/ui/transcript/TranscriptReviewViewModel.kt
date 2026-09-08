package com.yuukias.seminararc.ui.transcript

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.yuukias.seminararc.domain.model.TranscriptState
import com.yuukias.seminararc.domain.repository.SeminarRepository
import com.yuukias.seminararc.domain.repository.TranscriptRepository
import com.yuukias.seminararc.domain.usecase.BuildTranscriptTimelineWindowsUseCase
import com.yuukias.seminararc.domain.usecase.TranscriptTimelineWindowInput
import com.yuukias.seminararc.domain.usecase.TranscriptTimelineWindowResult
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
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class TranscriptReviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val seminarRepository: SeminarRepository,
    private val transcriptRepository: TranscriptRepository,
    private val buildTranscriptTimelineWindows: BuildTranscriptTimelineWindowsUseCase,
) : ViewModel() {
    private val seminarId: Long = savedStateHandle["seminarId"]
        ?: savedStateHandle.toRoute<TranscriptReviewRoute>().seminarId

    private val selectedTranscriptId = MutableStateFlow<Long?>(null)

    private val _events = MutableSharedFlow<TranscriptReviewEvent>(replay = 0)
    val events: SharedFlow<TranscriptReviewEvent> = _events.asSharedFlow()

    val uiState: StateFlow<TranscriptReviewUiState> = combine(
        seminarRepository.observeSeminarDetail(seminarId),
        transcriptRepository.observeTranscripts(seminarId),
        transcriptRepository.observeSummaryDrafts(seminarId),
        selectedTranscriptId,
    ) { detail, transcripts, drafts, selectedId ->
        TranscriptReviewSnapshot(detail, transcripts, drafts, selectedId)
    }
        .mapLatest { snapshot -> snapshot.toUiState() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TranscriptReviewUiState.Loading)

    fun onTranscriptSelected(transcriptId: Long) {
        selectedTranscriptId.value = transcriptId
    }

    fun onDraftSummaryClicked() {
        viewModelScope.launch {
            _events.emit(TranscriptReviewEvent.ShowMessage("Summary drafting is not wired to a live provider yet."))
        }
    }

    fun onRunTranscriptionClicked() {
        viewModelScope.launch {
            _events.emit(TranscriptReviewEvent.ShowMessage("Transcription provider selection is not wired yet."))
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
            timelineWindows = windows,
            summaryDrafts = summaryDrafts.sortedWith(compareByDescending { draft -> draft.updatedAt }),
        )
    }
}

private data class TranscriptReviewSnapshot(
    val detail: com.yuukias.seminararc.domain.model.SeminarDetail?,
    val transcripts: List<com.yuukias.seminararc.domain.model.Transcript>,
    val summaryDrafts: List<com.yuukias.seminararc.domain.model.SummaryDraft>,
    val selectedTranscriptId: Long?,
)
