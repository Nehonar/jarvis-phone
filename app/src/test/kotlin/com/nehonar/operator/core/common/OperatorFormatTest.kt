package com.nehonar.operator.core.common

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class OperatorFormatTest {

    @Test
    fun `formatea fecha ISO con codigo de dia en castellano`() {
        assertEquals("JUE 2026-07-02", formatOperatorDate(LocalDate.of(2026, 7, 2)))
        assertEquals("LUN 2026-07-06", formatOperatorDate(LocalDate.of(2026, 7, 6)))
        assertEquals("DOM 2026-07-05", formatOperatorDate(LocalDate.of(2026, 7, 5)))
    }

    @Test
    fun `formatea fecha y hora con ceros a la izquierda`() {
        assertEquals(
            "JUE 2026-07-02 09:05",
            formatOperatorDateTime(Instant.parse("2026-07-02T09:05:00Z"), ZoneOffset.UTC),
        )
    }

    @Test
    fun `formatea solo la hora con ceros a la izquierda`() {
        assertEquals("09:05", formatOperatorTime(Instant.parse("2026-07-02T09:05:00Z"), ZoneOffset.UTC))
        assertEquals("16:45", formatOperatorTime(Instant.parse("2026-07-02T16:45:00Z"), ZoneOffset.UTC))
    }
}
