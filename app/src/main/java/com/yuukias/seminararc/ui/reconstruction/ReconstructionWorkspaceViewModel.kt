package com.yuukias.seminararc.ui.reconstruction

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.yuukias.seminararc.data.storage.MediaStorageManager
import com.yuukias.seminararc.domain.image.ImageEnhancementOptions
import com.yuukias.seminararc.domain.model.ProcessingJobState
import com.yuukias.seminararc.domain.model.ProcessingJobType
import com.yuukias.seminararc.domain.model.SeminarSystemTag
import com.yuukias.seminararc.domain.ocr.TextOcrLanguageMode
import com.yuukias.seminararc.domain.repository.ReconstructionRepository
import com.yuukias.seminararc.domain.repository.SeminarRepository
import com.yuukias.seminararc.media.processing.ProcessingWorkScheduler
import com.yuukias.seminararc.ui.navigation.ReconstructionWorkspaceRoute
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
class ReconstructionWorkspaceViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val seminarRepository: SeminarRepository,
    private val reconstructionRepository: ReconstructionRepository,
    private val mediaStorageManager: MediaStorageManager,
    private val processingWorkScheduler: ProcessingWorkScheduler,
) : ViewModel() {
    private val seminarId: Long = savedStateHandle["seminarId"]
        ?: savedStateHandle.toRoute<ReconstructionWorkspaceRoute>().seminarId

    private val searchQuery = MutableStateFlow("")
    private val ocrStatusFilter = MutableStateFlow(OcrStatusFilter.ALL)
    private val keySlidesOnly = MutableStateFlow(false)

    private val _events = MutableSharedFlow<ReconstructionWorkspaceEvent>(replay = 0)
    val events: SharedFlow<ReconstructionWorkspaceEvent> = _events.asSharedFlow()

    private val dataSnapshot = combine(
        seminarRepository.observeSeminarDetail(seminarId),
        reconstructionRepository.observePhotoAssetsForSeminar(seminarId),
        reconstructionRepository.observeOcrResultsForSeminar(seminarId),
        reconstructionRepository.observeJobsForSeminar(seminarId),
        reconstructionRepository.observeAssetIdsForSystemTag(seminarId, SeminarSystemTag.KEY_SLIDE),
    ) { detail, photoAssets, ocrResults, jobs, keySlideAssetIds ->
        ReconstructionWorkspaceData(
            detail = detail,
            photoAssets = photoAssets,
            ocrResults = ocrResults,
            jobs = jobs,
            keySlideAssetIds = keySlideAssetIds.toSet(),
        )
    }

    val uiState: StateFlow<ReconstructionWorkspaceUiState> = combine(
        dataSnapshot,
        searchQuery,
        ocrStatusFilter,
        keySlidesOnly,
    ) { data, query, filter, showKeySlidesOnly ->
        ReconstructionWorkspaceInputs(
            detail = data.detail,
            photoAssets = data.photoAssets,
            ocrResults = data.ocrResults,
            jobs = data.jobs,
            keySlideAssetIds = data.keySlideAssetIds,
            searchQuery = query,
            ocrStatusFilter = filter,
            keySlidesOnly = showKeySlidesOnly,
        )
    }
        .mapLatest { inputs -> inputs.toUiState() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReconstructionWorkspaceUiState.Loading)

    fun onSearchQueryChanged(query: String) {
        searchQuery.value = query
    }

    fun onOcrStatusFilterChanged(filter: OcrStatusFilter) {
        ocrStatusFilter.value = filter
    }

    fun onKeySlidesOnlyChanged(enabled: Boolean) {
        keySlidesOnly.value = enabled
    }

    fun onKeySlideChanged(assetId: Long, enabled: Boolean) {
        viewModelScope.launch {
            reconstructionRepository.setSystemTag(assetId, SeminarSystemTag.KEY_SLIDE, enabled)
        }
    }

    fun onEditOcrResult(assetId: Long, editedText: String) {
        viewModelScope.launch {
            val updated = reconstructionRepository.editOcrResult(assetId, editedText)
            if (!updated) {
                _events.emit(ReconstructionWorkspaceEvent.ShowMessage("OCR result was not updated."))
            }
        }
    }

    fun onEnhancePhoto(assetId: Long, options: ImageEnhancementOptions = ImageEnhancementOptions()) {
        viewModelScope.launch {
            if (processingWorkScheduler.enqueueImageEnhancement(assetId, options) == null) {
                _events.emit(ReconstructionWorkspaceEvent.ShowMessage("Source asset was not found."))
            } else {
                _events.emit(ReconstructionWorkspaceEvent.ShowMessage("Image enhancement queued."))
            }
        }
    }

    fun onRunOcr(assetId: Long, languageMode: TextOcrLanguageMode = TextOcrLanguageMode.LATIN_AND_CHINESE) {
        viewModelScope.launch {
            if (processingWorkScheduler.enqueueTextOcr(assetId, languageMode) == null) {
                _events.emit(ReconstructionWorkspaceEvent.ShowMessage("Source asset was not found."))
            } else {
                _events.emit(ReconstructionWorkspaceEvent.ShowMessage("OCR queued."))
            }
        }
    }

    fun onRetryJob(jobId: Long) {
        viewModelScope.launch {
            if (processingWorkScheduler.retry(jobId) == null) {
                _events.emit(ReconstructionWorkspaceEvent.ShowMessage("Job was not found."))
            } else {
                _events.emit(ReconstructionWorkspaceEvent.ShowMessage("Processing retry queued."))
            }
        }
    }

    fun onCancelJob(jobId: Long) {
        viewModelScope.launch {
            processingWorkScheduler.cancel(jobId)
            _events.emit(ReconstructionWorkspaceEvent.ShowMessage("Processing job cancelled."))
        }
    }

    private suspend fun ReconstructionWorkspaceInputs.toUiState(): ReconstructionWorkspaceUiState {
        val currentDetail = detail ?: return ReconstructionWorkspaceUiState.Missing(seminarId)
        val ocrByAsset = ocrResults.associateBy { result -> result.assetId }
        val jobsByAsset = jobs.groupBy { job -> job.inputAssetId }
        val items = photoAssets.map { asset ->
            val file = asset.relativePath?.let { path -> mediaStorageManager.resolveReadableRelativeFile(path) }
            ReconstructionAssetUiItem(
                asset = asset,
                absolutePhotoPath = file?.absolutePath,
                photoMissing = asset.relativePath != null && file == null,
                ocrResult = ocrByAsset[asset.id],
                jobs = jobsByAsset[asset.id].orEmpty(),
                isKeySlide = asset.id in keySlideAssetIds,
            )
        }
        val visible = items
            .filter { item -> !keySlidesOnly || item.isKeySlide }
            .filter { item -> item.matchesOcrStatus(ocrStatusFilter) }
            .filter { item -> item.matchesQuery(searchQuery) }
        return ReconstructionWorkspaceUiState.Ready(
            detail = currentDetail,
            searchQuery = searchQuery,
            ocrStatusFilter = ocrStatusFilter,
            keySlidesOnly = keySlidesOnly,
            items = visible,
            totalPhotoCount = items.size,
            visiblePhotoCount = visible.size,
        )
    }

    private fun ReconstructionAssetUiItem.matchesOcrStatus(filter: OcrStatusFilter): Boolean {
        return when (filter) {
            OcrStatusFilter.ALL -> true
            OcrStatusFilter.HAS_OCR -> ocrResult != null
            OcrStatusFilter.NEEDS_OCR -> ocrResult == null && jobs.none { job ->
                job.type == ProcessingJobType.TEXT_OCR &&
                    job.state in listOf(ProcessingJobState.QUEUED, ProcessingJobState.RUNNING)
            }
            OcrStatusFilter.FAILED -> jobs.any { job -> job.type == ProcessingJobType.TEXT_OCR && job.state == ProcessingJobState.FAILED }
        }
    }

    private fun ReconstructionAssetUiItem.matchesQuery(query: String): Boolean {
        val normalized = query.trim()
        if (normalized.isBlank()) {
            return true
        }
        return listOfNotNull(
            asset.displayName,
            asset.relativePath,
            ocrResult?.recognizedText,
            ocrResult?.editedText,
        ).any { value -> value.contains(normalized, ignoreCase = true) }
    }
}

private data class ReconstructionWorkspaceData(
    val detail: com.yuukias.seminararc.domain.model.SeminarDetail?,
    val photoAssets: List<com.yuukias.seminararc.domain.model.SeminarAsset>,
    val ocrResults: List<com.yuukias.seminararc.domain.model.OcrResult>,
    val jobs: List<com.yuukias.seminararc.domain.model.ProcessingJob>,
    val keySlideAssetIds: Set<Long>,
)

private data class ReconstructionWorkspaceInputs(
    val detail: com.yuukias.seminararc.domain.model.SeminarDetail?,
    val photoAssets: List<com.yuukias.seminararc.domain.model.SeminarAsset>,
    val ocrResults: List<com.yuukias.seminararc.domain.model.OcrResult>,
    val jobs: List<com.yuukias.seminararc.domain.model.ProcessingJob>,
    val keySlideAssetIds: Set<Long>,
    val searchQuery: String,
    val ocrStatusFilter: OcrStatusFilter,
    val keySlidesOnly: Boolean,
)
