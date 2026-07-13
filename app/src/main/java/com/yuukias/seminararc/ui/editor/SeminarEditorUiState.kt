package com.yuukias.seminararc.ui.editor

import com.yuukias.seminararc.domain.model.AbstractAttachment
import java.time.Instant

sealed interface SeminarEditorUiState {
    data object Loading : SeminarEditorUiState

    data class Editing(
        val seminarId: Long?,
        val title: String,
        val speaker: String,
        val affiliation: String,
        val scheduledAt: Instant?,
        val location: String,
        val abstractText: String,
        val attachment: AbstractAttachment?,
        val pendingAttachmentUri: String?,
        val removeExistingAttachment: Boolean,
        val isSaving: Boolean,
        val errorMessage: String?,
    ) : SeminarEditorUiState
}
