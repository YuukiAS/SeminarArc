package com.yuukias.seminararc.media.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import com.yuukias.seminararc.domain.image.FractionalCrop
import com.yuukias.seminararc.domain.image.FractionalPerspective
import com.yuukias.seminararc.domain.image.ImageEnhancementOptions
import com.yuukias.seminararc.domain.image.ImageEnhancementProvider
import com.yuukias.seminararc.domain.image.ImageEnhancementResult
import com.yuukias.seminararc.domain.image.ReadabilityEnhancement
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidBitmapImageEnhancementProvider @Inject constructor() : ImageEnhancementProvider {
    override val providerId: String = "android-bitmap-local"
    override val providerVersion: String = "1"

    override suspend fun enhance(
        source: File,
        output: File,
        options: ImageEnhancementOptions,
    ): ImageEnhancementResult = withContext(Dispatchers.Default) {
        if (!source.isFile || !source.canRead()) {
            return@withContext ImageEnhancementResult.Failed("Source photo is not readable.")
        }
        val bitmap = decodeScaledBitmap(source)
            ?: return@withContext ImageEnhancementResult.Failed("Source photo could not be decoded.")
        try {
            val transformed = bitmap
                .rotate(options.rotationDegrees)
                .correctPerspective(options.perspective)
                .crop(options.crop)
                .enhanceReadability(options.readability)

            output.parentFile?.mkdirs()
            val quality = options.jpegQuality.coerceIn(
                ImageEnhancementOptions.MIN_JPEG_QUALITY,
                ImageEnhancementOptions.MAX_JPEG_QUALITY,
            )
            val saved = output.outputStream().use { stream ->
                transformed.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            }
            if (!saved) {
                output.delete()
                return@withContext ImageEnhancementResult.Failed("Enhanced photo could not be encoded.")
            }
            ImageEnhancementResult.Enhanced(
                file = output,
                width = transformed.width,
                height = transformed.height,
                mimeType = JPEG_MIME_TYPE,
            )
        } catch (throwable: Throwable) {
            output.delete()
            ImageEnhancementResult.Failed(throwable.message ?: "Image enhancement failed.")
        } finally {
            bitmap.recycle()
        }
    }

    private fun decodeScaledBitmap(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null
        }
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inSampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight)
        }
        return BitmapFactory.decodeFile(file.absolutePath, options)
    }

    private fun calculateSampleSize(width: Int, height: Int): Int {
        var sample = 1
        while ((width / sample) > MAX_DECODE_DIMENSION || (height / sample) > MAX_DECODE_DIMENSION) {
            sample *= 2
        }
        return sample
    }

    private fun Bitmap.rotate(degrees: Int): Bitmap {
        val normalized = ((degrees % 360) + 360) % 360
        if (normalized == 0) {
            return this
        }
        val matrix = Matrix().apply { postRotate(normalized.toFloat()) }
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true).also { rotated ->
            if (rotated !== this) {
                recycle()
            }
        }
    }

    private fun Bitmap.correctPerspective(perspective: FractionalPerspective?): Bitmap {
        perspective ?: return this
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val source = floatArrayOf(
            perspective.topLeft.x * width,
            perspective.topLeft.y * height,
            perspective.topRight.x * width,
            perspective.topRight.y * height,
            perspective.bottomRight.x * width,
            perspective.bottomRight.y * height,
            perspective.bottomLeft.x * width,
            perspective.bottomLeft.y * height,
        )
        val destination = floatArrayOf(
            0f,
            0f,
            width.toFloat(),
            0f,
            width.toFloat(),
            height.toFloat(),
            0f,
            height.toFloat(),
        )
        val matrix = Matrix()
        if (!matrix.setPolyToPoly(source, 0, destination, 0, 4)) {
            return this
        }
        Canvas(output).drawBitmap(this, matrix, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        recycle()
        return output
    }

    private fun Bitmap.crop(crop: FractionalCrop?): Bitmap {
        crop ?: return this
        val left = (crop.left * width).toInt().coerceIn(0, width - 1)
        val top = (crop.top * height).toInt().coerceIn(0, height - 1)
        val right = (crop.right * width).toInt().coerceIn(left + 1, width)
        val bottom = (crop.bottom * height).toInt().coerceIn(top + 1, height)
        val cropped = Bitmap.createBitmap(this, left, top, right - left, bottom - top)
        if (cropped !== this) {
            recycle()
        }
        return cropped
    }

    private fun Bitmap.enhanceReadability(readability: ReadabilityEnhancement): Bitmap {
        if (readability == ReadabilityEnhancement.NONE) {
            return this
        }
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val contrast = when (readability) {
            ReadabilityEnhancement.NONE -> 1f
            ReadabilityEnhancement.STANDARD -> 1.18f
            ReadabilityEnhancement.HIGH_CONTRAST -> 1.35f
        }
        val translate = (-128f * contrast) + 128f
        val contrastMatrix = ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, translate,
                0f, contrast, 0f, 0f, translate,
                0f, 0f, contrast, 0f, translate,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
        val saturation = ColorMatrix().apply {
            setSaturation(
                when (readability) {
                    ReadabilityEnhancement.NONE -> 1f
                    ReadabilityEnhancement.STANDARD -> 0.92f
                    ReadabilityEnhancement.HIGH_CONTRAST -> 0.75f
                },
            )
        }
        saturation.postConcat(contrastMatrix)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(saturation)
        }
        Canvas(output).drawBitmap(this, 0f, 0f, paint)
        recycle()
        return output
    }

    private companion object {
        const val MAX_DECODE_DIMENSION = 2_400
        const val JPEG_MIME_TYPE = "image/jpeg"
    }
}
