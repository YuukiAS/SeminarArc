package com.yuukias.seminararc.media.processing

import kotlinx.serialization.Serializable

@Serializable
data class FormulaOcrWorkPayload(
    val seminarId: Long,
    val regionId: Long,
    val manualLatex: String,
    val requestFingerprint: String,
) {
    init {
        require(seminarId > 0L) { "Seminar id must be positive." }
        require(regionId > 0L) { "Formula region id must be positive." }
        require(manualLatex.isNotBlank()) { "Manual LaTeX must not be blank." }
        require(requestFingerprint.isNotBlank()) { "Formula request fingerprint must not be blank." }
    }
}
