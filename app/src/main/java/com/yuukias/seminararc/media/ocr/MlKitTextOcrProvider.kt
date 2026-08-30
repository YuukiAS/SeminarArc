package com.yuukias.seminararc.media.ocr

import android.content.Context
import android.graphics.Rect
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.yuukias.seminararc.domain.ocr.TextOcrLanguageMode
import com.yuukias.seminararc.domain.ocr.TextOcrProvider
import com.yuukias.seminararc.domain.ocr.TextOcrRecognition
import com.yuukias.seminararc.domain.ocr.TextOcrResult
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class MlKitTextOcrProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : TextOcrProvider {
    override val providerId: String = "mlkit-text-recognition-bundled"
    override val providerVersion: String = "16.0.1"

    override suspend fun recognize(
        source: File,
        languageMode: TextOcrLanguageMode,
    ): TextOcrResult {
        if (!source.isFile || !source.canRead()) {
            return TextOcrResult.Failed("Source image is not readable.", isRetryable = false)
        }
        val image = try {
            InputImage.fromFilePath(context, Uri.fromFile(source))
        } catch (throwable: Throwable) {
            return TextOcrResult.Failed(throwable.message ?: "Source image could not be decoded.")
        }
        val recognizers = recognizersFor(languageMode)
        val texts = mutableListOf<Text>()
        for (recognizer in recognizers) {
            recognizer.processText(image).fold(
                onSuccess = { text -> texts += text },
                onFailure = { throwable ->
                    return TextOcrResult.Failed(throwable.message ?: "ML Kit text recognition failed.")
                },
            )
        }
        return TextOcrResult.Recognized(mergeTexts(texts, languageMode))
    }

    private fun recognizersFor(languageMode: TextOcrLanguageMode): List<TextRecognizer> {
        return when (languageMode) {
            TextOcrLanguageMode.LATIN -> listOf(
                TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS),
            )
            TextOcrLanguageMode.CHINESE -> listOf(
                TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build()),
            )
            TextOcrLanguageMode.LATIN_AND_CHINESE -> listOf(
                TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS),
                TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build()),
            )
        }
    }

    private suspend fun TextRecognizer.processText(image: InputImage): Result<Text> {
        return try {
            process(image).awaitText()
        } catch (throwable: Throwable) {
            Result.failure(throwable)
        } finally {
            try {
                close()
            } catch (_: Throwable) {
            }
        }
    }

    private fun mergeTexts(
        texts: List<Text>,
        languageMode: TextOcrLanguageMode,
    ): TextOcrRecognition {
        val uniqueBlocks = texts.flatMap { text -> text.textBlocks }
            .distinctBy { block -> block.text.trim() }
            .filter { block -> block.text.isNotBlank() }
        val recognizedText = uniqueBlocks.joinToString(separator = "\n\n") { block -> block.text }
        val blocks = uniqueBlocks.mapIndexed { index, block ->
            OcrBlockDto(
                index = index,
                text = block.text,
                boundingBox = block.boundingBox?.toDto(),
                lines = block.lines.map { line ->
                    OcrLineDto(
                        text = line.text,
                        boundingBox = line.boundingBox?.toDto(),
                    )
                },
            )
        }
        return TextOcrRecognition(
            recognizedText = recognizedText,
            blockJson = Json.encodeToString(blocks),
            languageHint = languageMode.name,
            confidence = null,
        )
    }

    private suspend fun Task<Text>.awaitText(): Result<Text> {
        return suspendCancellableCoroutine { continuation ->
            addOnSuccessListener { text -> continuation.resume(Result.success(text)) }
            addOnFailureListener { throwable -> continuation.resume(Result.failure(throwable)) }
            addOnCanceledListener { continuation.resume(Result.failure(CancellationException("ML Kit text recognition was cancelled."))) }
        }
    }

    private fun Rect.toDto(): OcrRectDto {
        return OcrRectDto(left = left, top = top, right = right, bottom = bottom)
    }
}

@Serializable
private data class OcrBlockDto(
    val index: Int,
    val text: String,
    val boundingBox: OcrRectDto?,
    val lines: List<OcrLineDto>,
)

@Serializable
private data class OcrLineDto(
    val text: String,
    val boundingBox: OcrRectDto?,
)

@Serializable
private data class OcrRectDto(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)
