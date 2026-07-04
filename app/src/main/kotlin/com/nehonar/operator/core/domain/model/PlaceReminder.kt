package com.nehonar.operator.core.domain.model

import java.time.Instant

/**
 * Recordatorio que salta al ENTRAR en un lugar guardado, no a una hora. Vive en
 * su propia tabla, separado de [Reminder] (por tiempo), ver docs/fase-13-plan.md.
 */
data class PlaceReminder(
    val id: String,
    val voiceNoteId: String,
    val message: String,
    val placeId: String,
    /** Etiqueta del lugar, desnormalizada para mostrar sin unir tablas. */
    val placeLabel: String,
    val status: ReminderStatus,
    val createdAt: Instant,
)
