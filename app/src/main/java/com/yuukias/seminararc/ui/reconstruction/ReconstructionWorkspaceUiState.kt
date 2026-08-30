package com.yuukias.seminararc.ui.reconstruction

import com.yuukias.seminararc.domain.model.OcrResult
import com.yuukias.seminararc.domain.model.ProcessingJob
import com.yuukias.seminararc.domain.model.SeminarAsset
import com.yuukias.seminararc.domain.model.SeminarDetail

sealed interface ReconstructionWorkspaceUiState {
    data object Loading : ReconstructionWorkspaceUiState

    data class Ready(
        val detail: SeminarDetail,
        val searchQuery: String,
        val ocrStatusFilter: OcrStatusFilter,
        val keySlidesOnly: Boolean,
        val items: List<ReconstructionAssetUiItem>,
        val totalPhotoCount: Int,
        val visiblePhotoCount: Int,
    ) : ReconstructionWorkspaceUiState

    data class Missing(val seminarId: Long) : ReconstructionWorkspaceUiState
}

data class ReconstructionAssetUiItem(
    val asset: SeminarAsset,
    val absolutePhotoPath: String?,
    val photoMissing: Boolean,
    val ocrResult: OcrResult?,
    val jobs: List<ProcessingJob>,
    val isKeySlide: Boolean,
) {
    val searchableText: String
        get() = ocrResult?.editedText ?: ocrResult?.recognizedText.orEmpty()
}

enum class OcrStatusFilter {
    ALL,
    HAS_OCR,
    NEEDS_OCR,
    FAILED,
}

sealed interface ReconstructionWorkspaceEvent {
    data class ShowMessage(val message: String) : ReconstructionWorkspaceEvent
}
