package com.yuukias.seminararc.domain.image

import java.io.File

data class ImageEnhancementOptions(
    val rotationDegrees: Int = 0,
    val crop: FractionalCrop? = null,
    val perspective: FractionalPerspective? = null,
    val readability: ReadabilityEnhancement = ReadabilityEnhancement.STANDARD,
    val jpegQuality: Int = DEFAULT_JPEG_QUALITY,
) {
    fun variantKey(): String {
        return listOf(
            "r${rotationDegrees.floorMod(360)}",
            crop?.variantKey() ?: "crop-none",
            perspective?.variantKey() ?: "persp-none",
            "read-${readability.name.lowercase()}",
            "q${jpegQuality.coerceIn(MIN_JPEG_QUALITY, MAX_JPEG_QUALITY)}",
        ).joinToString("-")
    }

    private fun Int.floorMod(modulus: Int): Int {
        return ((this % modulus) + modulus) % modulus
    }

    companion object {
        const val DEFAULT_JPEG_QUALITY = 92
        const val MIN_JPEG_QUALITY = 50
        const val MAX_JPEG_QUALITY = 100
    }
}

data class FractionalCrop(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    init {
        require(left in 0f..1f) { "Crop left must be between 0 and 1." }
        require(top in 0f..1f) { "Crop top must be between 0 and 1." }
        require(right in 0f..1f) { "Crop right must be between 0 and 1." }
        require(bottom in 0f..1f) { "Crop bottom must be between 0 and 1." }
        require(right > left) { "Crop right must be greater than left." }
        require(bottom > top) { "Crop bottom must be greater than top." }
    }

    fun variantKey(): String {
        return "crop-${left.toKey()}-${top.toKey()}-${right.toKey()}-${bottom.toKey()}"
    }
}

data class FractionalPerspective(
    val topLeft: FractionalPoint,
    val topRight: FractionalPoint,
    val bottomRight: FractionalPoint,
    val bottomLeft: FractionalPoint,
) {
    fun variantKey(): String {
        return "persp-${topLeft.variantKey()}-${topRight.variantKey()}-${bottomRight.variantKey()}-${bottomLeft.variantKey()}"
    }
}

data class FractionalPoint(
    val x: Float,
    val y: Float,
) {
    init {
        require(x in 0f..1f) { "Point x must be between 0 and 1." }
        require(y in 0f..1f) { "Point y must be between 0 and 1." }
    }

    fun variantKey(): String = "${x.toKey()}-${y.toKey()}"
}

enum class ReadabilityEnhancement {
    NONE,
    STANDARD,
    HIGH_CONTRAST,
}

sealed interface ImageEnhancementResult {
    data class Enhanced(
        val file: File,
        val width: Int,
        val height: Int,
        val mimeType: String,
    ) : ImageEnhancementResult

    data class Failed(val message: String) : ImageEnhancementResult
}

interface ImageEnhancementProvider {
    val providerId: String
    val providerVersion: String

    suspend fun enhance(
        source: File,
        output: File,
        options: ImageEnhancementOptions,
    ): ImageEnhancementResult
}

private fun Float.toKey(): String {
    return (this * 1_000).toInt().coerceIn(0, 1_000).toString()
}
