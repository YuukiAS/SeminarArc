package com.yuukias.seminararc.ui.detail

import com.yuukias.seminararc.domain.model.SeminarDetail

sealed interface SeminarDetailUiState {
    data object Loading : SeminarDetailUiState
    data class Ready(
        val detail: SeminarDetail,
        val isDeleting: Boolean,
        val showDeleteDialog: Boolean,
        val isStartingRecording: Boolean,
        val isExporting: Boolean,
        val exportMessage: String?,
        val recordingErrorMessage: String?,
        val recordingPlayback: RecordingPlaybackUiState,
    ) : SeminarDetailUiState
}
