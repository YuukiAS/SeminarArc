package com.yuukias.seminararc.ui.reference

import com.yuukias.seminararc.domain.model.ReferenceCandidate
import com.yuukias.seminararc.domain.model.ReferenceEvidence
import com.yuukias.seminararc.domain.model.ReferenceLookupAttempt
import com.yuukias.seminararc.domain.model.ReferenceQueryPreview
import com.yuukias.seminararc.domain.model.SeminarBrief
import com.yuukias.seminararc.domain.model.SeminarDetail

sealed interface ReferenceReviewUiState {
    data object Loading : ReferenceReviewUiState
    data class Missing(val seminarId: Long) : ReferenceReviewUiState
    data class Ready(
        val detail: SeminarDetail,
        val evidenceOptions: List<ReferenceEvidenceOption>,
        val persistedEvidence: List<ReferenceEvidence>,
        val selectedOptionIds: Set<String>,
        val manualText: String,
        val queryPreview: ReferenceQueryPreview?,
        val attempts: List<ReferenceLookupAttempt>,
        val candidates: List<ReferenceCandidate>,
        val brief: SeminarBrief?,
        val isLookupRunning: Boolean,
        val message: String?,
    ) : ReferenceReviewUiState
}

data class ReferenceEvidenceOption(
    val id: String,
    val sourceLabel: String,
    val selectedText: String,
    val sourceAssetId: Long?,
    val sourceId: Long?,
)

sealed interface ReferenceReviewEvent {
    data class ShowMessage(val message: String) : ReferenceReviewEvent
}
