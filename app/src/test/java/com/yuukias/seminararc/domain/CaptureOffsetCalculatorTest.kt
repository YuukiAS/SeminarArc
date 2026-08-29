package com.yuukias.seminararc.domain

import com.yuukias.seminararc.domain.usecase.CaptureOffsetAnchor
import com.yuukias.seminararc.domain.usecase.CaptureOffsetCalculator
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class CaptureOffsetCalculatorTest {

    private val calculator = CaptureOffsetCalculator()

    @Test
    fun offsetFrom_whenNowIsAfterRecordingStart_returnsElapsedMilliseconds() {
        val anchor = CaptureOffsetAnchor(
            recordingId = 7L,
            startedAt = Instant.parse("2026-08-29T10:00:00Z"),
        )

        val offset = calculator.offsetFrom(anchor, Instant.parse("2026-08-29T10:01:23.456Z"))

        assertEquals(83_456L, offset)
    }

    @Test
    fun offsetFrom_whenPhotoOnlySessionUsesSeminarStart_returnsElapsedMilliseconds() {
        val anchor = CaptureOffsetAnchor(
            recordingId = null,
            startedAt = Instant.parse("2026-08-29T10:00:00Z"),
        )

        val offset = calculator.offsetFrom(anchor, Instant.parse("2026-08-29T10:00:04.250Z"))

        assertEquals(4_250L, offset)
    }

    @Test
    fun offsetFrom_whenClockSkewsBeforeStart_clampsToZero() {
        val anchor = CaptureOffsetAnchor(
            recordingId = 1L,
            startedAt = Instant.parse("2026-08-29T10:00:00Z"),
        )

        val offset = calculator.offsetFrom(anchor, Instant.parse("2026-08-29T09:59:59Z"))

        assertEquals(0L, offset)
    }
}
