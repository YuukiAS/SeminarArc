package com.yuukias.seminararc.ui.reference

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.yuukias.seminararc.domain.model.ReferenceCandidateStatus
import com.yuukias.seminararc.domain.model.ReferenceEvidenceSourceType
import com.yuukias.seminararc.domain.model.ReferenceLookupAttemptState
import com.yuukias.seminararc.domain.model.SeminarSystemTag
import com.yuukias.seminararc.domain.repository.CreateReferenceEvidenceInput
import com.yuukias.seminararc.domain.repository.ReferenceRepository
import com.yuukias.seminararc.domain.repository.ReconstructionRepository
import com.yuukias.seminararc.domain.repository.SaveSeminarBriefInput
import com.yuukias.seminararc.domain.repository.SeminarRepository
import com.yuukias.seminararc.ui.navigation.ReferenceReviewRoute
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
class ReferenceReviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val seminarRepository: SeminarRepository,
    private val reconstructionRepository: ReconstructionRepository,
    private val referenceRepository: ReferenceRepository,
) : ViewModel() {
    private val seminarId: Long = savedStateHandle["seminarId"] ?: savedStateHandle.toRoute<ReferenceReviewRoute>().seminarId
    private val selectedOptionIds = MutableStateFlow<Set<String>>(emptySet())
    private val manualText = MutableStateFlow("")
    private val queryPreview = MutableStateFlow<com.yuukias.seminararc.domain.model.ReferenceQueryPreview?>(null)
    private val isLookupRunning = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)

    private val _events = MutableSharedFlow<ReferenceReviewEvent>(replay = 0)
    val events: SharedFlow<ReferenceReviewEvent> = _events.asSharedFlow()

    val uiState: StateFlow<ReferenceReviewUiState> = combine(
        seminarRepository.observeSeminarDetail(seminarId),
        reconstructionRepository.observeOcrResultsForSeminar(seminarId),
        reconstructionRepository.observePhotoAssetsForSeminar(seminarId),
        reconstructionRepository.observeAssetIdsForSystemTag(seminarId, SeminarSystemTag.KEY_SLIDE),
        referenceRepository.observeEvidence(seminarId),
        referenceRepository.observeAttempts(seminarId),
        referenceRepository.observeCandidates(seminarId),
        referenceRepository.observeBrief(seminarId),
        selectedOptionIds,
        manualText,
        queryPreview,
        isLookupRunning,
        message,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val detail = values[0] as com.yuukias.seminararc.domain.model.SeminarDetail?
        val ocrResults = values[1] as List<com.yuukias.seminararc.domain.model.OcrResult>
        val assets = values[2] as List<com.yuukias.seminararc.domain.model.SeminarAsset>
        val keySlides = (values[3] as List<Long>).toSet()
        val evidence = values[4] as List<com.yuukias.seminararc.domain.model.ReferenceEvidence>
        val attempts = values[5] as List<com.yuukias.seminararc.domain.model.ReferenceLookupAttempt>
        val candidates = values[6] as List<com.yuukias.seminararc.domain.model.ReferenceCandidate>
        val brief = values[7] as com.yuukias.seminararc.domain.model.SeminarBrief?
        val selected = values[8] as Set<String>
        val manual = values[9] as String
        val preview = values[10] as com.yuukias.seminararc.domain.model.ReferenceQueryPreview?
        val running = values[11] as Boolean
        val currentMessage = values[12] as String?
        ReferenceReviewInputs(detail, ocrResults, assets, keySlides, evidence, attempts, candidates, brief, selected, manual, preview, running, currentMessage)
    }.mapLatest { inputs ->
        val detail = inputs.detail ?: return@mapLatest ReferenceReviewUiState.Missing(seminarId)
        val ocrOptions = inputs.ocrResults.map { result ->
            ReferenceEvidenceOption(
                id = "ocr:${result.id}",
                sourceLabel = "OCR ${result.assetId}",
                selectedText = (result.editedText ?: result.recognizedText).take(500),
                sourceAssetId = result.assetId,
                sourceId = result.id,
            )
        }
        val keySlideOptions = inputs.assets.filter { it.id in inputs.keySlides }.map { asset ->
            ReferenceEvidenceOption(
                id = "asset:${asset.id}",
                sourceLabel = "Key slide ${asset.displayName ?: asset.id}",
                selectedText = asset.displayName ?: asset.relativePath ?: "Key slide ${asset.id}",
                sourceAssetId = asset.id,
                sourceId = asset.id,
            )
        }
        ReferenceReviewUiState.Ready(
            detail = detail,
            evidenceOptions = (ocrOptions + keySlideOptions).distinctBy { it.id },
            persistedEvidence = inputs.evidence,
            selectedOptionIds = inputs.selectedOptionIds,
            manualText = inputs.manualText,
            queryPreview = inputs.queryPreview,
            attempts = inputs.attempts,
            candidates = inputs.candidates,
            brief = inputs.brief,
            isLookupRunning = inputs.isLookupRunning,
            message = inputs.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReferenceReviewUiState.Loading)

    fun onEvidenceOptionChanged(optionId: String, selected: Boolean) {
        selectedOptionIds.value = if (selected) selectedOptionIds.value + optionId else selectedOptionIds.value - optionId
        queryPreview.value = null
    }

    fun onManualTextChanged(text: String) {
        manualText.value = text
        queryPreview.value = null
    }

    fun onBuildPreview() {
        val ready = uiState.value as? ReferenceReviewUiState.Ready ?: return
        viewModelScope.launch {
            val ids = ready.evidenceOptions
                .filter { it.id in ready.selectedOptionIds }
                .map { option ->
                    referenceRepository.createEvidence(
                        CreateReferenceEvidenceInput(
                            seminarId = ready.detail.id,
                            sourceType = if (option.id.startsWith("ocr:")) ReferenceEvidenceSourceType.OCR_RESULT else ReferenceEvidenceSourceType.ASSET,
                            sourceId = option.sourceId,
                            sourceAssetId = option.sourceAssetId,
                            selectedText = option.selectedText,
                        ),
                    ).id
                }.toMutableList()
            if (ready.manualText.isNotBlank()) {
                ids += referenceRepository.createEvidence(
                    CreateReferenceEvidenceInput(
                        seminarId = ready.detail.id,
                        sourceType = ReferenceEvidenceSourceType.USER_INPUT,
                        sourceId = null,
                        sourceAssetId = null,
                        selectedText = ready.manualText,
                    ),
                ).id
            }
            val preview = referenceRepository.buildQueryPreview(ready.detail.id, ids, "")
            queryPreview.value = preview
            if (preview == null) _events.emit(ReferenceReviewEvent.ShowMessage("Select evidence or enter a DOI/title clue first."))
        }
    }

    fun onRunLookup() {
        val preview = queryPreview.value ?: return
        viewModelScope.launch {
            isLookupRunning.value = true
            val summary = referenceRepository.runLookup(preview)
            isLookupRunning.value = false
            val latest = summary.attempts.firstOrNull()
            val text = when (latest?.state) {
                ReferenceLookupAttemptState.SUCCEEDED -> "Reference lookup complete."
                ReferenceLookupAttemptState.RATE_LIMITED -> latest.errorMessage ?: "Provider rate limited this lookup."
                ReferenceLookupAttemptState.FAILED -> latest.errorMessage ?: "Reference lookup failed."
                else -> "Reference lookup updated."
            }
            _events.emit(ReferenceReviewEvent.ShowMessage(text))
        }
    }

    fun onCancelLookup() {
        val preview = queryPreview.value ?: return
        viewModelScope.launch {
            referenceRepository.cancelLookup(preview.requestFingerprint)
            isLookupRunning.value = false
            _events.emit(ReferenceReviewEvent.ShowMessage("Reference lookup cancelled."))
        }
    }

    fun onReviewCandidate(candidateId: Long, status: ReferenceCandidateStatus) {
        viewModelScope.launch {
            referenceRepository.reviewCandidate(candidateId, status)
        }
    }

    fun onEnsureBrief() {
        viewModelScope.launch {
            referenceRepository.getOrCreateBrief(seminarId)
        }
    }

    fun onLinkSelectedKeySlides() {
        val ready = uiState.value as? ReferenceReviewUiState.Ready ?: return
        viewModelScope.launch {
            val brief = referenceRepository.getOrCreateBrief(seminarId)
            val linked = ready.evidenceOptions
                .filter { it.id in ready.selectedOptionIds && it.id.startsWith("asset:") }
                .mapNotNull { option ->
                    option.sourceAssetId?.let { assetId ->
                        referenceRepository.setBriefKeySlide(
                            briefId = brief.id,
                            assetId = assetId,
                            included = true,
                            caption = option.sourceLabel,
                        )
                    }
                }
                .count { it }
            _events.emit(ReferenceReviewEvent.ShowMessage("$linked key slides linked to Seminar Brief."))
        }
    }

    fun onSaveBrief(input: SaveSeminarBriefInput) {
        viewModelScope.launch {
            referenceRepository.saveBrief(input)
            _events.emit(ReferenceReviewEvent.ShowMessage("Seminar Brief saved."))
        }
    }
}

private data class ReferenceReviewInputs(
    val detail: com.yuukias.seminararc.domain.model.SeminarDetail?,
    val ocrResults: List<com.yuukias.seminararc.domain.model.OcrResult>,
    val assets: List<com.yuukias.seminararc.domain.model.SeminarAsset>,
    val keySlides: Set<Long>,
    val evidence: List<com.yuukias.seminararc.domain.model.ReferenceEvidence>,
    val attempts: List<com.yuukias.seminararc.domain.model.ReferenceLookupAttempt>,
    val candidates: List<com.yuukias.seminararc.domain.model.ReferenceCandidate>,
    val brief: com.yuukias.seminararc.domain.model.SeminarBrief?,
    val selectedOptionIds: Set<String>,
    val manualText: String,
    val queryPreview: com.yuukias.seminararc.domain.model.ReferenceQueryPreview?,
    val isLookupRunning: Boolean,
    val message: String?,
)
