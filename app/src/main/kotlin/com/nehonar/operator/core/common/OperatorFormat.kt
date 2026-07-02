package com.nehonar.operator.core.common

import java.time.LocalDate

private val DAY_CODES = listOf("LUN", "MAR", "MIÉ", "JUE", "VIE", "SÁB", "DOM")

fun formatOperatorDate(date: LocalDate): String =
    "${DAY_CODES[date.dayOfWeek.value - 1]} $date"
