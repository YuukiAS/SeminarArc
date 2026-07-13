package com.yuukias.seminararc.ui.library

import com.yuukias.seminararc.domain.model.SeminarListFilter
import com.yuukias.seminararc.domain.model.SeminarSummary

sealed interface SeminarLibraryUiState {
    data object Loading : SeminarLibraryUiState

    data class Ready(
        val query: String,
        val filter: SeminarListFilter,
        val seminars: List<SeminarSummary>,
    ) : SeminarLibraryUiState
}
