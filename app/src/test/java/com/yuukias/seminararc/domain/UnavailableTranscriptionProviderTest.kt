package com.yuukias.seminararc.domain

import com.yuukias.seminararc.domain.model.TranscriptLanguageHint
import com.yuukias.seminararc.domain.transcription.TranscriptionRequest
import com.yuukias.seminararc.domain.transcription.TranscriptionResult
import com.yuukias.seminararc.media.transcription.UnavailableTranscriptionProvider
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class UnavailableTranscriptionProviderTest {
    @Test
    fun transcribeReturnsNonRetryableUnavailableFailure() = runTest {
        val provider = UnavailableTranscriptionProvider()

        val result = provider.transcribe(
            TranscriptionRequest(
                seminarId = 1L,
                recordingId = 2L,
                sourceAudio = File("source.m4a"),
                languageHint = TranscriptLanguageHint.AUTO,
                sourceAssetId = 3L,
            ),
        ) as TranscriptionResult.Failed

        assertEquals("unavailable-transcription", provider.providerId)
        assertEquals("Live transcription provider is not configured.", result.message)
        assertEquals(false, result.isRetryable)
    }
}
