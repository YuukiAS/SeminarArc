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
import com.yuukias.seminararc.domain.repository.CreateFormulaRegionInput
import com.yuukias.seminararc.domain.repository.FormulaRepository
import com.yuukias.seminararc.domain.repository.ReconstructionRepository
import com.yuukias.seminararc.domain.repository.SeminarRepository
import com.yuukias.seminararc.domain.repository.UpdateFormulaRegionInput
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
    private val formulaRepository: FormulaRepository,
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

    private val contentSnapshot = combine(
        seminarRepository.observeSeminarDetail(seminarId),
        reconstructionRepository.observePhotoAssetsForSeminar(seminarId),
        reconstructionRepository.observeOcrResultsForSeminar(seminarId),
        formulaRepository.observeRegionsForSeminar(seminarId),
        formulaRepository.observeResultsForSeminar(seminarId),
    ) { detail, photoAssets, ocrResults, formulaRegions, formulaResults ->
        ReconstructionWorkspaceData(
            detail = detail,
            photoAssets = photoAssets,
            ocrResults = ocrResults,
            formulaRegions = formulaRegions,
            formulaResults = formulaResults,
            jobs = emptyList(),
            keySlideAssetIds = emptySet(),
        )
    }
    private val dataSnapshot = combine(
        contentSnapshot,
        reconstructionRepository.observeJobsForSeminar(seminarId),
        reconstructionRepository.observeAssetIdsForSystemTag(seminarId, SeminarSystemTag.KEY_SLIDE),
    ) { data, jobs, keySlideAssetIds ->
        data.copy(
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
            formulaRegions = data.formulaRegions,
            formulaResults = data.formulaResults,
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

    fun onAddFormulaRegion(
        assetId: Long,
        label: String,
        normalizedX: Float,
        normalizedY: Float,
        normalizedWidth: Float,
        normalizedHeight: Float,
    ) {
        viewModelScope.launch {
            val created = runCatching {
                formulaRepository.createRegion(
                    CreateFormulaRegionInput(
                        seminarId = seminarId,
                        sourceAssetId = assetId,
                        normalizedX = normalizedX,
                        normalizedY = normalizedY,
                        normalizedWidth = normalizedWidth,
                        normalizedHeight = normalizedHeight,
                        label = label,
                    ),
                )
            }.getOrNull()
            if (created == null) {
                _events.emit(ReconstructionWorkspaceEvent.ShowMessage("Formula region was not saved."))
            } else {
                _events.emit(ReconstructionWorkspaceEvent.ShowMessage("Formula region saved."))
            }
        }
    }

    fun onDeleteFormulaRegion(regionId: Long) {
        viewModelScope.launch {
            if (!formulaRepository.deleteRegion(regionId)) {
                _events.emit(ReconstructionWorkspaceEvent.ShowMessage("Formula region was not deleted."))
            }
        }
    }

    fun onUpdateFormulaRegion(
        regionId: Long,
        label: String,
        normalizedX: Float,
        normalizedY: Float,
        normalizedWidth: Float,
        normalizedHeight: Float,
    ) {
        viewModelScope.launch {
            val updated = runCatching {
                formulaRepository.updateRegion(
                    UpdateFormulaRegionInput(
                        regionId = regionId,
                        normalizedX = normalizedX,
                        normalizedY = normalizedY,
                        normalizedWidth = normalizedWidth,
                        normalizedHeight = normalizedHeight,
                        label = label,
                    ),
                )
            }.getOrNull()
            if (updated == null) {
                _events.emit(ReconstructionWorkspaceEvent.ShowMessage("Formula region was not updated."))
            } else {
                _events.emit(ReconstructionWorkspaceEvent.ShowMessage("Formula region updated."))
            }
        }
    }

    fun onSaveFormulaLatex(regionId: Long, latex: String) {
        viewModelScope.launch {
            if (processingWorkScheduler.enqueueManualFormulaOcr(regionId, latex) == null) {
                _events.emit(ReconstructionWorkspaceEvent.ShowMessage("Formula LaTeX was not queued."))
            } else {
                _events.emit(ReconstructionWorkspaceEvent.ShowMessage("Formula LaTeX queued."))
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
        val formulaRegionsByAsset = formulaRegions.groupBy { region -> region.sourceAssetId }
        val formulaResultsByAsset = formulaResults.groupBy { result ->
            formulaRegions.firstOrNull { region -> region.id == result.regionId }?.sourceAssetId ?: -1L
        }
        val jobsByAsset = jobs.groupBy { job -> job.inputAssetId }
        val items = photoAssets.map { asset ->
            val file = asset.relativePath?.let { path -> mediaStorageManager.resolveReadableRelativeFile(path) }
            ReconstructionAssetUiItem(
                asset = asset,
                absolutePhotoPath = file?.absolutePath,
                photoMissing = asset.relativePath != null && file == null,
                ocrResult = ocrByAsset[asset.id],
                formulaRegions = formulaRegionsByAsset[asset.id].orEmpty(),
                formulaResults = formulaResultsByAsset[asset.id].orEmpty(),
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
            formulaRegions.joinToString(" ") { region -> region.label.orEmpty() },
            formulaResults.joinToString(" ") { result -> result.latex },
        ).any { value -> value.contains(normalized, ignoreCase = true) }
    }
}

private data class ReconstructionWorkspaceData(
    val detail: com.yuukias.seminararc.domain.model.SeminarDetail?,
    val photoAssets: List<com.yuukias.seminararc.domain.model.SeminarAsset>,
    val ocrResults: List<com.yuukias.seminararc.domain.model.OcrResult>,
    val formulaRegions: List<com.yuukias.seminararc.domain.model.FormulaRegion>,
    val formulaResults: List<com.yuukias.seminararc.domain.model.FormulaResult>,
    val jobs: List<com.yuukias.seminararc.domain.model.ProcessingJob>,
    val keySlideAssetIds: Set<Long>,
)

private data class ReconstructionWorkspaceInputs(
    val detail: com.yuukias.seminararc.domain.model.SeminarDetail?,
    val photoAssets: List<com.yuukias.seminararc.domain.model.SeminarAsset>,
    val ocrResults: List<com.yuukias.seminararc.domain.model.OcrResult>,
    val formulaRegions: List<com.yuukias.seminararc.domain.model.FormulaRegion>,
    val formulaResults: List<com.yuukias.seminararc.domain.model.FormulaResult>,
    val jobs: List<com.yuukias.seminararc.domain.model.ProcessingJob>,
    val keySlideAssetIds: Set<Long>,
    val searchQuery: String,
    val ocrStatusFilter: OcrStatusFilter,
    val keySlidesOnly: Boolean,
)
