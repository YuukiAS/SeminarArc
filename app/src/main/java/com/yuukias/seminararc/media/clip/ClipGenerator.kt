package com.yuukias.seminararc.media.clip

import java.io.File

sealed interface ClipGenerationResult {
    data object Generated : ClipGenerationResult
    data class Failed(val message: String) : ClipGenerationResult
}

interface ClipGenerator {
    suspend fun generate(
        source: File,
        output: File,
        startOffsetMs: Long,
        endOffsetMs: Long,
    ): ClipGenerationResult
}
