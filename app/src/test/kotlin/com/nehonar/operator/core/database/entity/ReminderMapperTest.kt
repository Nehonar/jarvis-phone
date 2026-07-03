package com.nehonar.operator.core.database.entity

import com.nehonar.operator.core.domain.model.Reminder
import com.nehonar.operator.core.domain.model.ReminderStatus
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderMapperTest {

    @Test
    fun `entity a dominio y vuelta conserva todos los campos`() {
        val reminder = Reminder(
            id = "r1",
            voiceNoteId = "n1",
            message = "Recuerda ir al médico",
            triggerAt = Instant.ofEpochMilli(1_750_000_000_000),
            status = ReminderStatus.PENDING,
        )
        assertEquals(reminder, reminder.toEntity().toDomain())
    }

    @Test
    fun `status desconocido en base de datos mapea a DISMISSED`() {
        val entity = ReminderEntity(
            id = "r2",
            voiceNoteId = "n1",
            message = "x",
            triggerAtEpochMillis = 0,
            status = "ESTADO_FUTURO",
        )
        assertEquals(ReminderStatus.DISMISSED, entity.toDomain().status)
    }
}
