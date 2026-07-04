package com.nehonar.operator.core.calendar

import java.time.Instant

data class CalendarEvent(
    val id: String,
    val title: String,
    val startAt: Instant,
    val endAt: Instant,
    val allDay: Boolean,
)
