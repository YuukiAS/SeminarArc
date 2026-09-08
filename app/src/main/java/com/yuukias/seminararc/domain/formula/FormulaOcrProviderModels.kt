package com.yuukias.seminararc.domain.formula

import java.io.File

enum class FormulaLanguageHint {
    AUTO,
    LATEX,
    ENGLISH,
    CHINESE,
    MIXED,
}

data class FormulaRegionCrop(
    val normalizedX: Float,
    val normalizedY: Float,
    val normalizedWidth: Float,
    val normalizedHeight: Float,
) {
    init {
        require(normalizedX in 0f..1f) { "Crop x must be between 0 and 1." }
        require(normalizedY in 0f..1f) { "Crop y must be between 0 and 1." }
        require(normalizedWidth > 0f && normalizedWidth <= 1f) { "Crop width must be greater than 0 and at most 1." }
        require(normalizedHeight > 0f && normalizedHeight <= 1f) { "Crop height must be greater than 0 and at most 1." }
        require(normalizedX + normalizedWidth <= 1.0001f) { "Crop must fit within the source image width." }
        require(normalizedY + normalizedHeight <= 1.0001f) { "Crop must fit within the source image height." }
    }
}

data class FormulaOcrRequest(
    val seminarId: Long,
    val regionId: Long,
    val sourceAssetId: Long,
    val sourcePhoto: File,
    val crop: FormulaRegionCrop,
    val rotationDegrees: Int = 0,
    val languageHint: FormulaLanguageHint = FormulaLanguageHint.AUTO,
    val contextHint: String? = null,
    val requestFingerprint: String,
    val manualLatex: String? = null,
) {
    init {
        require(seminarId > 0L) { "Seminar id must be positive." }
        require(regionId > 0L) { "Region id must be positive." }
        require(sourceAssetId > 0L) { "Source asset id must be positive." }
        require(rotationDegrees in -359..359) { "Rotation degrees must be between -359 and 359." }
        require(requestFingerprint.isNotBlank()) { "Request fingerprint must not be blank." }
    }
}

data class FormulaRecognition(
    val latex: String,
    val confidence: Float?,
    val provenanceJson: String = "{}",
) {
    init {
        require(latex.isNotBlank()) { "LaTeX must not be blank." }
        confidence?.let { require(it in 0f..1f) { "Formula confidence must be between 0 and 1." } }
    }
}

sealed interface FormulaOcrResult {
    data class Recognized(val recognition: FormulaRecognition) : FormulaOcrResult
    data class Failed(val message: String, val isRetryable: Boolean = true) : FormulaOcrResult {
        init {
            require(message.isNotBlank()) { "Failure message must not be blank." }
        }
    }
}

interface FormulaOcrProvider {
    val providerId: String
    val providerVersion: String

    suspend fun recognize(request: FormulaOcrRequest): FormulaOcrResult
}
