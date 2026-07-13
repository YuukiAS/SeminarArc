package com.yuukias.seminararc.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val userZone: ZoneId = ZoneId.systemDefault()
private val dayTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy - h:mm a")
private val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")
private val dateInputFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")

fun Instant?.formatSeminarDateTime(): String {
    return this?.atZone(userZone)?.format(dayTimeFormatter) ?: "No date scheduled"
}

fun Instant?.formatMonthBucket(): String {
    return this?.atZone(userZone)?.format(monthFormatter) ?: "Unscheduled"
}

fun Instant?.formatDateInput(): String {
    return this?.atZone(userZone)?.format(dateInputFormatter) ?: "Pick date and time"
}
