package com.yuukias.seminararc.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuukias.seminararc.domain.model.SeminarListFilter
import com.yuukias.seminararc.domain.repository.SeminarRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class SeminarLibraryViewModel @Inject constructor(
    private val seminarRepository: SeminarRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(SeminarListFilter.ALL)

    val uiState: StateFlow<SeminarLibraryUiState> = combine(query, filter) { currentQuery, currentFilter ->
        currentQuery to currentFilter
    }.flatMapLatest { (currentQuery, currentFilter) ->
        seminarRepository.observeSeminars(
            filter = currentFilter,
            query = currentQuery,
        ).combine(query) { seminars, latestQuery ->
            SeminarLibraryUiState.Ready(
                query = latestQuery,
                filter = currentFilter,
                seminars = seminars,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SeminarLibraryUiState.Loading,
    )

    fun onQueryChanged(value: String) {
        query.value = value
    }

    fun onFilterChanged(value: SeminarListFilter) {
        filter.value = value
    }
}
