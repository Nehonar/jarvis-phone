package com.nehonar.operator.core.common

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class OperatorFormatTest {

    @Test
    fun `formatea fecha ISO con codigo de dia en castellano`() {
        assertEquals("JUE 2026-07-02", formatOperatorDate(LocalDate.of(2026, 7, 2)))
        assertEquals("LUN 2026-07-06", formatOperatorDate(LocalDate.of(2026, 7, 6)))
        assertEquals("DOM 2026-07-05", formatOperatorDate(LocalDate.of(2026, 7, 5)))
    }
}
