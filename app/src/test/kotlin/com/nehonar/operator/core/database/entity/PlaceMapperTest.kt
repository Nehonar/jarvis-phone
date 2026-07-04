package com.nehonar.operator.core.database.entity

import com.nehonar.operator.core.domain.model.PlaceReminder
import com.nehonar.operator.core.domain.model.ReminderStatus
import com.nehonar.operator.core.domain.model.SavedPlace
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaceMapperTest {

    @Test
    fun `savedplace entity a dominio y vuelta conserva todos los campos`() {
        val place = SavedPlace(
            id = "p1",
            label = "Casa",
            latitude = 40.4168,
            longitude = -3.7038,
            radiusMeters = 150f,
            createdAt = Instant.ofEpochMilli(1_750_000_000_000),
        )
        assertEquals(place, place.toEntity().toDomain())
    }

    @Test
    fun `placereminder entity a dominio y vuelta conserva todos los campos`() {
        val reminder = PlaceReminder(
            id = "r1",
            voiceNoteId = "n1",
            message = "Sacar la basura",
            placeId = "p1",
            placeLabel = "Casa",
            status = ReminderStatus.PENDING,
            createdAt = Instant.ofEpochMilli(1_750_000_000_000),
        )
        assertEquals(reminder, reminder.toEntity().toDomain())
    }

    @Test
    fun `placereminder con estado desconocido mapea a DISMISSED`() {
        val entity = PlaceReminderEntity(
            id = "r2",
            voiceNoteId = "n1",
            message = "x",
            placeId = "p1",
            placeLabel = "Casa",
            status = "ESTADO_FUTURO",
            createdAtEpochMillis = 0,
        )
        assertEquals(ReminderStatus.DISMISSED, entity.toDomain().status)
    }
}
