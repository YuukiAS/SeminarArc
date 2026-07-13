package com.yuukias.seminararc.ui.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.yuukias.seminararc.domain.model.SeminarDraftInput
import com.yuukias.seminararc.domain.model.SeminarStatus
import com.yuukias.seminararc.domain.repository.SeminarRepository
import com.yuukias.seminararc.ui.navigation.SeminarEditorRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant

sealed interface SeminarEditorEvent {
    data class Saved(val seminarId: Long) : SeminarEditorEvent
}

@HiltViewModel
class SeminarEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val seminarRepository: SeminarRepository,
) : ViewModel() {

    private val route: SeminarEditorRoute = savedStateHandle.toRoute()
    private val _uiState = MutableStateFlow<SeminarEditorUiState>(SeminarEditorUiState.Loading)
    val uiState: StateFlow<SeminarEditorUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<SeminarEditorEvent>(replay = 0)
    val events: SharedFlow<SeminarEditorEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            val existing = route.seminarId?.let(seminarRepository::getSeminarEditorData)
            _uiState.value = SeminarEditorUiState.Editing(
                seminarId = route.seminarId,
                title = existing?.draft?.title.orEmpty(),
                speaker = existing?.draft?.speaker.orEmpty(),
                affiliation = existing?.draft?.affiliation.orEmpty(),
                scheduledAt = existing?.draft?.scheduledAt,
                location = existing?.draft?.location.orEmpty(),
                abstractText = existing?.draft?.abstractText.orEmpty(),
                attachment = existing?.abstractAttachment,
                pendingAttachmentUri = null,
                removeExistingAttachment = false,
                isSaving = false,
                errorMessage = null,
            )
        }
    }

    fun onTitleChanged(value: String) = updateEditing { copy(title = value, errorMessage = null) }
    fun onSpeakerChanged(value: String) = updateEditing { copy(speaker = value) }
    fun onAffiliationChanged(value: String) = updateEditing { copy(affiliation = value) }
    fun onLocationChanged(value: String) = updateEditing { copy(location = value) }
    fun onAbstractTextChanged(value: String) = updateEditing { copy(abstractText = value) }
    fun onScheduledAtChanged(value: Instant?) = updateEditing { copy(scheduledAt = value) }

    fun onPdfSelected(sourceUri: String) = updateEditing {
        copy(
            pendingAttachmentUri = sourceUri,
            removeExistingAttachment = false,
        )
    }

    fun onRemovePdfClicked() = updateEditing {
        copy(
            pendingAttachmentUri = null,
            removeExistingAttachment = true,
        )
    }

    fun onSave() {
        val current = _uiState.value as? SeminarEditorUiState.Editing ?: return
        val title = current.title.trim()
        if (title.isBlank()) {
            updateEditing { copy(errorMessage = "Title is required.") }
            return
        }

        viewModelScope.launch {
            updateEditing { copy(isSaving = true, errorMessage = null) }
            val seminarId = seminarRepository.saveSeminar(
                SeminarDraftInput(
                    id = current.seminarId,
                    title = title,
                    speaker = current.speaker,
                    affiliation = current.affiliation,
                    scheduledAt = current.scheduledAt,
                    location = current.location,
                    abstractText = current.abstractText,
                    status = SeminarStatus.DRAFT,
                    rating = null,
                    isFavorite = false,
                ),
            )

            if (current.removeExistingAttachment) {
                seminarRepository.removeAbstractPdf(seminarId)
            }
            current.pendingAttachmentUri?.let { seminarRepository.importAbstractPdf(seminarId, it) }

            _events.emit(SeminarEditorEvent.Saved(seminarId))
            updateEditing { copy(isSaving = false) }
        }
    }

    private fun updateEditing(transform: SeminarEditorUiState.Editing.() -> SeminarEditorUiState.Editing) {
        val current = _uiState.value as? SeminarEditorUiState.Editing ?: return
        _uiState.value = current.transform()
    }
}
