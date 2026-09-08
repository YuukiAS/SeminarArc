package com.yuukias.seminararc.domain

import com.yuukias.seminararc.domain.model.TranscriptLanguageHint
import com.yuukias.seminararc.domain.transcription.TranscriptionProvider
import com.yuukias.seminararc.domain.transcription.TranscriptionRecognition
import com.yuukias.seminararc.domain.transcription.TranscriptionRequest
import com.yuukias.seminararc.domain.transcription.TranscriptionResult
import com.yuukias.seminararc.domain.transcription.TranscriptionSegmentDraft
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TranscriptionProviderContractTest {
    @Test
    fun fakeProviderReturnsTimestampedSegments() = runTest {
        val provider = FakeTranscriptionProvider(
            TranscriptionResult.Transcribed(
                TranscriptionRecognition(
                    languageHint = TranscriptLanguageHint.MIXED,
                    segments = listOf(
                        TranscriptionSegmentDraft(
                            startOffsetMs = 1_000L,
                            endOffsetMs = 2_500L,
                            text = "Sparse recovery starts here.",
                            confidence = 0.92f,
                        ),
                    ),
                    provenanceJson = """{"fixture":"contract"}""",
                ),
            ),
        )
        val request = TranscriptionRequest(
            seminarId = 1L,
            recordingId = 2L,
            sourceAudio = File("seminars/1/recordings/source.m4a"),
            languageHint = TranscriptLanguageHint.MIXED,
            sourceAssetId = 3L,
        )

        val result = provider.transcribe(request) as TranscriptionResult.Transcribed

        assertEquals("fake-transcription", provider.providerId)
        assertEquals(request, provider.lastRequest)
        assertEquals(1_000L, result.recognition.segments.single().startOffsetMs)
        assertEquals(2_500L, result.recognition.segments.single().endOffsetMs)
        assertEquals(TranscriptLanguageHint.MIXED, result.recognition.languageHint)
    }

    @Test
    fun segmentDraftRejectsInvalidOffsetsAndBlankText() {
        assertThrows(IllegalArgumentException::class.java) {
            TranscriptionSegmentDraft(startOffsetMs = -1L, endOffsetMs = 100L, text = "text")
        }
        assertThrows(IllegalArgumentException::class.java) {
            TranscriptionSegmentDraft(startOffsetMs = 100L, endOffsetMs = 100L, text = "text")
        }
        assertThrows(IllegalArgumentException::class.java) {
            TranscriptionSegmentDraft(startOffsetMs = 0L, endOffsetMs = 100L, text = " ")
        }
    }

    @Test
    fun failedResultCarriesRetryability() = runTest {
        val provider = FakeTranscriptionProvider(TranscriptionResult.Failed("model missing", isRetryable = false))

        val result = provider.transcribe(
            TranscriptionRequest(
                seminarId = 1L,
                recordingId = 2L,
                sourceAudio = File("seminars/1/recordings/source.m4a"),
            ),
        ) as TranscriptionResult.Failed

        assertEquals("model missing", result.message)
        assertEquals(false, result.isRetryable)
    }
}

private class FakeTranscriptionProvider(
    private val result: TranscriptionResult,
) : TranscriptionProvider {
    override val providerId: String = "fake-transcription"
    override val providerVersion: String = "1"
    var lastRequest: TranscriptionRequest? = null
        private set

    override suspend fun transcribe(request: TranscriptionRequest): TranscriptionResult {
        lastRequest = request
        return result
    }
}
