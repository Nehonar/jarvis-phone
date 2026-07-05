package com.nehonar.operator.core.calendar

interface CalendarRepository {
    fun hasPermission(): Boolean

    /** Eventos de hoy (zona local), ordenados por inicio. Sin permiso: lista vacía. */
    suspend fun getEventsForToday(): List<CalendarEvent>

    /** Eventos desde el inicio de hoy hasta el final del día (hoy + [days] - 1). */
    suspend fun getEventsForDays(days: Int): List<CalendarEvent>
}
