package com.yuukias.seminararc.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.yuukias.seminararc.domain.repository.SeminarRepository
import com.yuukias.seminararc.ui.navigation.SeminarDetailRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

sealed interface SeminarDetailEvent {
    data object Deleted : SeminarDetailEvent
}

@HiltViewModel
class SeminarDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val seminarRepository: SeminarRepository,
) : ViewModel() {

    private val route: SeminarDetailRoute = savedStateHandle.toRoute()
    private val _uiState = MutableStateFlow<SeminarDetailUiState>(SeminarDetailUiState.Loading)
    val uiState = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<SeminarDetailEvent>(replay = 0)
    val events: SharedFlow<SeminarDetailEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            seminarRepository.observeSeminarDetail(route.seminarId).collectLatest { detail ->
                detail?.let {
                    val current = _uiState.value as? SeminarDetailUiState.Ready
                    _uiState.value = SeminarDetailUiState.Ready(
                        detail = it,
                        isDeleting = current?.isDeleting ?: false,
                        showDeleteDialog = current?.showDeleteDialog ?: false,
                    )
                }
            }
        }
    }

    fun onFavoriteToggle() {
        val current = _uiState.value as? SeminarDetailUiState.Ready ?: return
        viewModelScope.launch {
            seminarRepository.setFavorite(current.detail.id, !current.detail.isFavorite)
        }
    }

    fun onRatingSelected(rating: Int) {
        val current = _uiState.value as? SeminarDetailUiState.Ready ?: return
        viewModelScope.launch {
            seminarRepository.setRating(current.detail.id, rating)
        }
    }

    fun onDeleteDialogChanged(show: Boolean) {
        val current = _uiState.value as? SeminarDetailUiState.Ready ?: return
        _uiState.value = current.copy(showDeleteDialog = show)
    }

    fun onDeleteConfirmed() {
        val current = _uiState.value as? SeminarDetailUiState.Ready ?: return
        viewModelScope.launch {
            _uiState.value = current.copy(isDeleting = true)
            seminarRepository.deleteSeminar(current.detail.id)
            _events.emit(SeminarDetailEvent.Deleted)
        }
    }
}
