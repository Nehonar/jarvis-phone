package com.nehonar.operator.feature.conversation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WakePhraseMatcherTest {

    @Test
    fun `solo la frase devuelve remainder vacio`() {
        assertEquals("", WakePhraseMatcher.match("Operador", "operador")?.remainder)
    }

    @Test
    fun `frase con orden pegada devuelve la orden`() {
        assertEquals(
            "apunta comprar pan",
            WakePhraseMatcher.match("Operador, apunta comprar pan", "operador")?.remainder,
        )
    }

    @Test
    fun `sin la frase devuelve null`() {
        assertNull(WakePhraseMatcher.match("hola qué tal", "operador"))
    }

    @Test
    fun `frase vacia nunca coincide`() {
        assertNull(WakePhraseMatcher.match("lo que sea", ""))
    }

    @Test
    fun `frase personalizada de varias palabras`() {
        assertEquals(
            "pon una alarma",
            WakePhraseMatcher.match("oye jefe pon una alarma", "oye jefe")?.remainder,
        )
    }
}
