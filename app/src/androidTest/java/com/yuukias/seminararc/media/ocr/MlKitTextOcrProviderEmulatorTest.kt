package com.yuukias.seminararc.media.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yuukias.seminararc.domain.ocr.TextOcrLanguageMode
import com.yuukias.seminararc.domain.ocr.TextOcrResult
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MlKitTextOcrProviderEmulatorTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val provider = MlKitTextOcrProvider(context)

    @Test
    fun bundledLatinRecognizerReadsGeneratedSlide() = runBlocking {
        val image = createTextImage("LOCAL OCR\nLATIN SLIDE", "latin-slide.jpg")

        val result = provider.recognize(image, TextOcrLanguageMode.LATIN)

        assertTrue(result is TextOcrResult.Recognized)
        val text = (result as TextOcrResult.Recognized).recognition.recognizedText.uppercase()
        assertTrue(text.contains("OCR") || text.contains("LATIN"))
    }

    @Test
    fun bundledChineseRecognizerRunsOnGeneratedSlide() = runBlocking {
        val image = createTextImage("本地视觉重建\n中文幻灯片", "chinese-slide.jpg")

        val result = provider.recognize(image, TextOcrLanguageMode.CHINESE)

        assertTrue(result is TextOcrResult.Recognized)
        assertTrue((result as TextOcrResult.Recognized).recognition.recognizedText.isNotBlank())
    }

    @Test
    fun bundledMixedRecognizersRunTogether() = runBlocking {
        val image = createTextImage("MIXED OCR TEST\nSeminarArc 2026\n本地重建", "mixed-slide.jpg")

        val result = provider.recognize(image, TextOcrLanguageMode.LATIN_AND_CHINESE)

        assertTrue(result is TextOcrResult.Recognized)
        val text = (result as TextOcrResult.Recognized).recognition.recognizedText
        assertTrue(text.isNotBlank())
    }

    @Test
    fun emptyLowTextImageReturnsRecognizedEmptyResult() = runBlocking {
        val image = createBlankImage("empty-slide.jpg")

        val result = provider.recognize(image, TextOcrLanguageMode.LATIN_AND_CHINESE)

        assertTrue(result is TextOcrResult.Recognized)
        assertTrue((result as TextOcrResult.Recognized).recognition.recognizedText.isBlank())
    }

    @Test
    fun missingAssetFailsWithoutUploadOrApiKey() = runBlocking {
        val missing = File(context.cacheDir, "missing-ocr-source.jpg")
        if (missing.exists()) missing.delete()

        val result = provider.recognize(missing, TextOcrLanguageMode.LATIN_AND_CHINESE)

        assertTrue(result is TextOcrResult.Failed)
        assertTrue(!(result as TextOcrResult.Failed).isRetryable)
    }

    private fun createTextImage(
        text: String,
        name: String,
    ): File {
        val file = File(context.cacheDir, name)
        val bitmap = Bitmap.createBitmap(900, 520, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 64f
        }
        text.lines().forEachIndexed { index, line ->
            canvas.drawText(line, 60f, 150f + (index * 100f), paint)
        }
        file.outputStream().use { output -> bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output) }
        bitmap.recycle()
        return file
    }

    private fun createBlankImage(name: String): File {
        val file = File(context.cacheDir, name)
        val bitmap = Bitmap.createBitmap(600, 360, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawColor(Color.WHITE)
        file.outputStream().use { output -> bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output) }
        bitmap.recycle()
        return file
    }
}
