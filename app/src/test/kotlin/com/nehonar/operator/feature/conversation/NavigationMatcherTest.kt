package com.nehonar.operator.feature.conversation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NavigationMatcherTest {

    @Test
    fun `abre los recordatorios navega a REMINDERS`() {
        assertEquals(OperatorDestination.REMINDERS, NavigationMatcher.match("ábreme los recordatorios"))
    }

    @Test
    fun `muestrame la memoria navega a MEMORY`() {
        assertEquals(OperatorDestination.MEMORY, NavigationMatcher.match("muéstrame la memoria"))
    }

    @Test
    fun `abre los lugares navega a PLACES`() {
        assertEquals(OperatorDestination.PLACES, NavigationMatcher.match("abre los lugares"))
    }

    @Test
    fun `llevame a los ajustes navega a SETTINGS`() {
        assertEquals(OperatorDestination.SETTINGS, NavigationMatcher.match("llévame a los ajustes"))
    }

    @Test
    fun `una pregunta sobre recordatorios no navega`() {
        assertNull(NavigationMatcher.match("¿qué recordatorios tengo mañana?"))
    }

    @Test
    fun `una orden de recordatorio no navega`() {
        assertNull(NavigationMatcher.match("recuérdame comprar pan"))
    }

    @Test
    fun `un verbo de apertura sin seccion conocida no navega`() {
        assertNull(NavigationMatcher.match("abre la ventana"))
    }
}
