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

        assertFalse(prompt.contains("Agenda del usuario"))
    }

    @Test
    fun `con agenda incluye los eventos y la regla de resolucion`() {
        val prompt = PromptBuilder.systemPrompt(
            today,
            agenda = listOf("HOY 11:00 Reunión de equipo", "MAÑANA (todo el día) Cumpleaños de Marc"),
        )

        assertTrue(prompt.contains("Agenda del usuario"))
        assertTrue(prompt.contains("- HOY 11:00 Reunión de equipo"))
        assertTrue(prompt.contains("- MAÑANA (todo el día) Cumpleaños de Marc"))
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
    fun `sin lugares no incluye el bloque de lugares`() {
        val prompt = PromptBuilder.systemPrompt(today)

        assertFalse(prompt.contains("Lugares guardados"))
    }

    @Test
    fun `con lugares incluye el bloque y la regla NEAR_LOCATION`() {
        val prompt = PromptBuilder.systemPrompt(today, places = listOf("casa", "trabajo"))

        assertTrue(prompt.contains("Lugares guardados del usuario"))
        assertTrue(prompt.contains("- casa"))
        assertTrue(prompt.contains("- trabajo"))
        assertTrue(prompt.contains("NEAR_LOCATION"))
    }

    @Test
    fun `mantiene la regla de hora ambigua`() {
        val prompt = PromptBuilder.systemPrompt(today)

        assertTrue(prompt.contains("time_of_day"))
        assertTrue(prompt.contains("¿De la mañana o de la tarde?"))
    }

    @Test
    fun `incluye el tipo QUERY en el schema y su regla`() {
        val prompt = PromptBuilder.systemPrompt(today)

        assertTrue(prompt.contains("QUERY"))
        assertTrue(prompt.contains("Preguntas del usuario"))
        assertTrue(prompt.contains("assistant_response"))
    }

    @Test
    fun `incluye el tipo DELETE, su campo y su regla`() {
        val prompt = PromptBuilder.systemPrompt(today)

        assertTrue(prompt.contains("DELETE"))
        assertTrue(prompt.contains("delete_query"))
        assertTrue(prompt.contains("Borrar"))
        assertTrue(prompt.contains("Contexto de conversación"))
    }

    @Test
    fun `sin estado actual no incluye el bloque de estado`() {
        val prompt = PromptBuilder.systemPrompt(today)

        assertFalse(prompt.contains("Estado actual del operador"))
    }

    @Test
    fun `con estado actual incluye el bloque para responder preguntas`() {
        val prompt = PromptBuilder.systemPrompt(
            today,
            currentState = listOf(
                "Recordatorio MAÑANA 08:00 — Médico",
                "Pendiente (SHOPPING): leche",
            ),
        )

        assertTrue(prompt.contains("Estado actual del operador"))
        assertTrue(prompt.contains("- Recordatorio MAÑANA 08:00 — Médico"))
        assertTrue(prompt.contains("- Pendiente (SHOPPING): leche"))
    }
}
