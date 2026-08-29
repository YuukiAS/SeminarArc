package com.yuukias.seminararc.media.clip

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidM4aClipGenerator @Inject constructor() : ClipGenerator {
    override suspend fun generate(
        source: File,
        output: File,
        startOffsetMs: Long,
        endOffsetMs: Long,
    ): ClipGenerationResult = withContext(Dispatchers.IO) {
        if (!source.isFile || !source.canRead()) {
            return@withContext ClipGenerationResult.Failed("Source recording file is missing.")
        }
        if (endOffsetMs <= startOffsetMs) {
            return@withContext ClipGenerationResult.Failed("Clip interval is empty.")
        }
        output.parentFile?.mkdirs()
        val partial = File(output.parentFile, ".${output.name}.tmp-${System.nanoTime()}")
        try {
            muxAudioTrack(source, partial, startOffsetMs, endOffsetMs)
            if (!partial.isFile || partial.length() <= 0L) {
                partial.delete()
                return@withContext ClipGenerationResult.Failed("Generated clip is empty.")
            }
            if (output.exists()) {
                output.delete()
            }
            if (!partial.renameTo(output)) {
                partial.copyTo(output, overwrite = true)
                partial.delete()
            }
            ClipGenerationResult.Generated
        } catch (throwable: Throwable) {
            partial.delete()
            output.delete()
            ClipGenerationResult.Failed(throwable.message ?: "Clip generation failed.")
        }
    }

    private fun muxAudioTrack(
        source: File,
        output: File,
        startOffsetMs: Long,
        endOffsetMs: Long,
    ) {
        val startUs = startOffsetMs * 1_000L
        val endUs = endOffsetMs * 1_000L
        val extractor = MediaExtractor()
        val muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        try {
            extractor.setDataSource(source.absolutePath)
            val sourceTrack = findAudioTrack(extractor)
            check(sourceTrack >= 0) { "Source recording has no audio track." }
            extractor.selectTrack(sourceTrack)
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val format = extractor.getTrackFormat(sourceTrack)
            val muxerTrack = muxer.addTrack(format)
            muxer.start()
            val bufferSize = resolveBufferSize(format)
            val buffer = java.nio.ByteBuffer.allocate(bufferSize)
            val info = MediaCodec.BufferInfo()
            while (true) {
                val sampleTimeUs = extractor.sampleTime
                if (sampleTimeUs < 0 || sampleTimeUs > endUs) break
                buffer.clear()
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break
                info.set(
                    0,
                    sampleSize,
                    (sampleTimeUs - startUs).coerceAtLeast(0L),
                    extractor.sampleFlags.toBufferInfoFlags(),
                )
                muxer.writeSampleData(muxerTrack, buffer, info)
                extractor.advance()
            }
        } finally {
            extractor.release()
            runCatching { muxer.stop() }
            muxer.release()
        }
    }

    private fun findAudioTrack(extractor: MediaExtractor): Int {
        for (index in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith("audio/") == true) return index
        }
        return -1
    }

    private fun resolveBufferSize(format: MediaFormat): Int {
        return if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
            format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE).coerceAtLeast(DEFAULT_BUFFER_SIZE)
        } else {
            DEFAULT_BUFFER_SIZE
        }
    }

    private fun Int.toBufferInfoFlags(): Int {
        var flags = 0
        if ((this and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
            flags = flags or MediaCodec.BUFFER_FLAG_KEY_FRAME
        }
        if ((this and MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME) != 0) {
            flags = flags or MediaCodec.BUFFER_FLAG_PARTIAL_FRAME
        }
        return flags
    }

    private companion object {
        const val DEFAULT_BUFFER_SIZE = 256 * 1024
    }
}
