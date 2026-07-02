package com.nehonar.operator.core.common

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private val DAY_CODES = listOf("LUN", "MAR", "MIÉ", "JUE", "VIE", "SÁB", "DOM")

fun formatOperatorDate(date: LocalDate): String =
    "${DAY_CODES[date.dayOfWeek.value - 1]} $date"

fun formatOperatorDateTime(instant: Instant, zone: ZoneId): String {
    val dateTime = instant.atZone(zone)
    val hour = dateTime.hour.toString().padStart(2, '0')
    val minute = dateTime.minute.toString().padStart(2, '0')
    return "${formatOperatorDate(dateTime.toLocalDate())} $hour:$minute"
}
