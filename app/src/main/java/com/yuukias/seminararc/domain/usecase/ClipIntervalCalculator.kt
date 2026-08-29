package com.yuukias.seminararc.domain.usecase

import javax.inject.Inject

data class ClipInterval(
    val startOffsetMs: Long,
    val endOffsetMs: Long,
    val isReadyForGeneration: Boolean,
)

class ClipIntervalCalculator @Inject constructor() {
    fun calculate(
        markOffsetMs: Long,
        recordingDurationMs: Long?,
        nowOffsetMs: Long? = null,
    ): ClipInterval {
        val safeMark = markOffsetMs.coerceAtLeast(0L)
        val start = (safeMark - PRE_ROLL_MS).coerceAtLeast(0L)
        val requestedEnd = safeMark + POST_ROLL_MS
        val availableEnd = recordingDurationMs ?: nowOffsetMs
        val ready = recordingDurationMs != null || (availableEnd != null && availableEnd >= requestedEnd)
        val end = when {
            recordingDurationMs != null -> requestedEnd.coerceAtMost(recordingDurationMs.coerceAtLeast(0L))
            availableEnd != null -> requestedEnd.coerceAtMost(availableEnd.coerceAtLeast(0L))
            else -> requestedEnd
        }
        return ClipInterval(
            startOffsetMs = start,
            endOffsetMs = end.coerceAtLeast(start),
            isReadyForGeneration = ready,
        )
    }

    companion object {
        const val PRE_ROLL_MS = 60_000L
        const val POST_ROLL_MS = 90_000L
    }
}
