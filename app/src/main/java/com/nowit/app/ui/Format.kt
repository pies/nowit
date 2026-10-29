package com.nowit.app.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val dateTime = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
private val date = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
private val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

private fun zoned(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())

fun formatDateTime(millis: Long): String = dateTime.format(zoned(millis))
fun formatDate(millis: Long): String = date.format(zoned(millis))
fun formatTime(millis: Long): String = time.format(zoned(millis))

fun formatDuration(ms: Long): String {
    val sign = if (ms < 0) "-" else "+"
    val m = kotlin.math.abs(ms) / 60_000
    return when {
        m < 60 -> "$sign${m}m"
        m < 60 * 24 -> "$sign${m / 60}h${m % 60}m"
        else -> "$sign${m / (60 * 24)}d"
    }
}
