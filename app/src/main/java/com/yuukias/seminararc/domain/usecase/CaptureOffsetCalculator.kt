package com.yuukias.seminararc.domain.usecase

import java.time.Duration
import java.time.Instant
import javax.inject.Inject

data class CaptureOffsetAnchor(
    val recordingId: Long?,
    val startedAt: Instant,
)

class CaptureOffsetCalculator @Inject constructor() {
    fun offsetFrom(anchor: CaptureOffsetAnchor, now: Instant): Long {
        return Duration.between(anchor.startedAt, now).toMillis().coerceAtLeast(0L)
    }
}
