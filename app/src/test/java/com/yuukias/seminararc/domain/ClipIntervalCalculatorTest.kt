package com.yuukias.seminararc.domain

import com.yuukias.seminararc.domain.usecase.ClipIntervalCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipIntervalCalculatorTest {
    private val calculator = ClipIntervalCalculator()

    @Test
    fun calculate_nearStart_clampsStartToZero() {
        val interval = calculator.calculate(markOffsetMs = 20_000L, recordingDurationMs = 200_000L)

        assertEquals(0L, interval.startOffsetMs)
        assertEquals(110_000L, interval.endOffsetMs)
        assertTrue(interval.isReadyForGeneration)
    }

    @Test
    fun calculate_nearEnd_clampsEndToRecordingDuration() {
        val interval = calculator.calculate(markOffsetMs = 180_000L, recordingDurationMs = 200_000L)

        assertEquals(120_000L, interval.startOffsetMs)
        assertEquals(200_000L, interval.endOffsetMs)
        assertTrue(interval.isReadyForGeneration)
    }

    @Test
    fun calculate_withoutDurationBeforePostRollAvailable_isNotReady() {
        val interval = calculator.calculate(
            markOffsetMs = 120_000L,
            recordingDurationMs = null,
            nowOffsetMs = 140_000L,
        )

        assertEquals(60_000L, interval.startOffsetMs)
        assertEquals(140_000L, interval.endOffsetMs)
        assertFalse(interval.isReadyForGeneration)
    }

    @Test
    fun calculate_negativeMark_clampsOffsets() {
        val interval = calculator.calculate(markOffsetMs = -5_000L, recordingDurationMs = 10_000L)

        assertEquals(0L, interval.startOffsetMs)
        assertEquals(10_000L, interval.endOffsetMs)
        assertTrue(interval.isReadyForGeneration)
    }
}
