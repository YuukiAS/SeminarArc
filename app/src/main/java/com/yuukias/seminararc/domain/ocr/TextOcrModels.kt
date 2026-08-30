package com.yuukias.seminararc.domain.ocr

import java.io.File

enum class TextOcrLanguageMode {
    LATIN,
    CHINESE,
    LATIN_AND_CHINESE,
}

data class TextOcrRecognition(
    val recognizedText: String,
    val blockJson: String?,
    val languageHint: String?,
    val confidence: Float?,
)

sealed interface TextOcrResult {
    data class Recognized(val recognition: TextOcrRecognition) : TextOcrResult
    data class Failed(val message: String, val isRetryable: Boolean = true) : TextOcrResult
}

interface TextOcrProvider {
    val providerId: String
    val providerVersion: String

    suspend fun recognize(
        source: File,
        languageMode: TextOcrLanguageMode = TextOcrLanguageMode.LATIN_AND_CHINESE,
    ): TextOcrResult
}
