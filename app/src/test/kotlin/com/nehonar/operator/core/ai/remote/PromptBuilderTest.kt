package com.nehonar.operator.core.ai.remote

import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptBuilderTest {

    private val today = LocalDate.of(2026, 7, 2)

    @Test
    fun `incluye la fecha actual con el dia de la semana`() {
        val prompt = PromptBuilder.systemPrompt(today)

        assertTrue(prompt.contains("Fecha actual: 2026-07-02 (jueves)"))
    }

    @Test
    fun `sin agenda no incluye el bloque de agenda`() {
        val prompt = PromptBuilder.systemPrompt(today)

        assertFalse(prompt.contains("Agenda de hoy"))
    }

    @Test
    fun `con agenda incluye los eventos y la regla de resolucion`() {
        val prompt = PromptBuilder.systemPrompt(
            today,
            agenda = listOf("11:00 Reunión de equipo", "(todo el día) Cumpleaños de Marc"),
        )

        assertTrue(prompt.contains("Agenda de hoy del usuario"))
        assertTrue(prompt.contains("- 11:00 Reunión de equipo"))
        assertTrue(prompt.contains("- (todo el día) Cumpleaños de Marc"))
        assertTrue(prompt.contains("usa su hora real"))
    }

    @Test
    fun `sin memoria no incluye el bloque de memoria`() {
        val prompt = PromptBuilder.systemPrompt(today)

        assertFalse(prompt.contains("Memoria del operador"))
    }

    @Test
    fun `con memoria incluye los hechos`() {
        val prompt = PromptBuilder.systemPrompt(
            today,
            memoryFacts = listOf("talla de pie: El usuario calza un 42"),
        )

        assertTrue(prompt.contains("Memoria del operador"))
        assertTrue(prompt.contains("- talla de pie: El usuario calza un 42"))
    }

    @Test
    fun `incluye la regla de busqueda cercana y el tipo NEARBY_SEARCH`() {
        val prompt = PromptBuilder.systemPrompt(today)

        assertTrue(prompt.contains("NEARBY_SEARCH"))
        assertTrue(prompt.contains("map_query"))
        assertTrue(prompt.contains("Búsqueda cercana"))
    }

    @Test
    fun `mantiene la regla de hora ambigua`() {
        val prompt = PromptBuilder.systemPrompt(today)

        assertTrue(prompt.contains("time_of_day"))
        assertTrue(prompt.contains("¿De la mañana o de la tarde?"))
    }
}
