package com.nehonar.operator.core.database.entity

import com.nehonar.operator.core.ai.IntentType
import com.nehonar.operator.core.ai.ParsedIntent
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParsedIntentMapperTest {

    @Test
    fun `entity a dominio y vuelta conserva date y time`() {
        val intent = ParsedIntent(
            intentType = IntentType.REMINDER,
            confidence = 0.8f,
            title = "REMINDER",
            summary = "ir al médico",
            assistantResponse = "Recordatorio programado, señor.",
            date = "2026-07-04",
            time = "09:00",
        )
        val roundTripped = intent.toEntity("n1", Instant.ofEpochMilli(1_000)).toDomain()

        assertEquals("2026-07-04", roundTripped.date)
        assertEquals("09:00", roundTripped.time)
        assertEquals(intent.copy(), roundTripped)
    }

    @Test
    fun `date y time nulos se conservan como null`() {
        val intent = ParsedIntent(
            intentType = IntentType.SHOPPING,
            confidence = 0.8f,
            title = "SHOPPING",
            summary = "comprar fruta",
            assistantResponse = "Recado detectado, señor.",
        )
        val roundTripped = intent.toEntity("n1", Instant.ofEpochMilli(1_000)).toDomain()

        assertNull(roundTripped.date)
        assertNull(roundTripped.time)
    }
}
