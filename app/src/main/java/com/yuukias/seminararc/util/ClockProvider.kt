package com.yuukias.seminararc.util

import java.time.Instant

fun interface ClockProvider {
    fun now(): Instant
}
