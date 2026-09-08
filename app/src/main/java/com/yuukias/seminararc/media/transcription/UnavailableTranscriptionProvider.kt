package com.yuukias.seminararc.media.transcription

import com.yuukias.seminararc.domain.transcription.TranscriptionProvider
import com.yuukias.seminararc.domain.transcription.TranscriptionRequest
import com.yuukias.seminararc.domain.transcription.TranscriptionResult
import javax.inject.Inject

class UnavailableTranscriptionProvider @Inject constructor() : TranscriptionProvider {
    override val providerId: String = "unavailable-transcription"
    override val providerVersion: String = "0.4-local-boundary"

    override suspend fun transcribe(request: TranscriptionRequest): TranscriptionResult {
        return TranscriptionResult.Failed(
            message = "Live transcription provider is not configured.",
            isRetryable = false,
        )
    }
}
