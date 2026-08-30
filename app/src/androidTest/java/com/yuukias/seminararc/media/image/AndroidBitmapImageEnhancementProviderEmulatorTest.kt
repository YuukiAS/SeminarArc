package com.yuukias.seminararc.media.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yuukias.seminararc.domain.image.FractionalCrop
import com.yuukias.seminararc.domain.image.FractionalPerspective
import com.yuukias.seminararc.domain.image.FractionalPoint
import com.yuukias.seminararc.domain.image.ImageEnhancementOptions
import com.yuukias.seminararc.domain.image.ImageEnhancementResult
import com.yuukias.seminararc.domain.image.ReadabilityEnhancement
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidBitmapImageEnhancementProviderEmulatorTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val provider = AndroidBitmapImageEnhancementProvider()

    @Test
    fun rotateCropPerspectiveAndReadabilityWriteReadableDerivedJpegAndPreserveOriginal() = runBlocking {
        val source = createSourceImage()
        val originalLength = source.length()
        val output = File(context.cacheDir, "enhanced-derived.jpg")
        if (output.exists()) output.delete()

        val result = provider.enhance(
            source = source,
            output = output,
            options = ImageEnhancementOptions(
                rotationDegrees = 90,
                crop = FractionalCrop(left = 0.05f, top = 0.05f, right = 0.95f, bottom = 0.95f),
                perspective = FractionalPerspective(
                    topLeft = FractionalPoint(0.02f, 0.03f),
                    topRight = FractionalPoint(0.96f, 0.02f),
                    bottomRight = FractionalPoint(0.98f, 0.96f),
                    bottomLeft = FractionalPoint(0.03f, 0.98f),
                ),
                readability = ReadabilityEnhancement.HIGH_CONTRAST,
                jpegQuality = 90,
            ),
        )

        assertTrue(result is ImageEnhancementResult.Enhanced)
        assertEquals(originalLength, source.length())
        assertTrue(output.isFile)
        val decoded = BitmapFactory.decodeFile(output.absolutePath)
        assertTrue(decoded != null)
        assertTrue(decoded!!.width > 0)
        assertTrue(decoded.height > 0)
        decoded.recycle()
    }

    @Test
    fun missingSourceFailsWithoutCreatingOutput() = runBlocking {
        val source = File(context.cacheDir, "missing-enhancement-source.jpg")
        if (source.exists()) source.delete()
        val output = File(context.cacheDir, "missing-enhancement-output.jpg")
        if (output.exists()) output.delete()

        val result = provider.enhance(source, output, ImageEnhancementOptions())

        assertTrue(result is ImageEnhancementResult.Failed)
        assertTrue(!output.exists())
    }

    private fun createSourceImage(): File {
        val file = File(context.cacheDir, "enhancement-source.jpg")
        val bitmap = Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(25, 25, 25)
            textSize = 42f
        }
        canvas.drawText("SeminarArc", 48f, 110f, paint)
        canvas.drawText("Readability Enhancement", 48f, 180f, paint)
        file.outputStream().use { output -> bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output) }
        bitmap.recycle()
        return file
    }
}
