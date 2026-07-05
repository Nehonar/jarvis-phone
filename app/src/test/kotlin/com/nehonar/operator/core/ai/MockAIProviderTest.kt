package com.nehonar.operator.core.ai

import com.nehonar.operator.testing.FixedTimeProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MockAIProviderTest {

    // 2026-07-02T10:15:00Z -> today() = 2026-07-02 (jueves), mañana = 2026-07-03.
    private val timeProvider = FixedTimeProvider()
    private val provider: AIProvider = MockAIProvider(timeProvider)

    private suspend fun parse(transcript: String): ParsedIntent =
        when (val result = provider.parseVoiceNote(transcript)) {
            is AIParseResult.Success -> result.intent
            is AIParseResult.Failure -> error("MockAIProvider no debería fallar nunca: ${result.reason}")
        }

    @Test
    fun `comprar detecta SHOPPING y extrae los items`() = runTest {
        val result = parse("comprar fruta y yogures")

        assertEquals(IntentType.SHOPPING, result.intentType)
        assertEquals(
            listOf("fruta", "yogures"),
            result.actions.filter { it.type == ActionType.BUY }.map { it.label },
        )
    }

    @Test
    fun `llevar detecta CARRY_ITEMS y extrae los items`() = runTest {
        val result = parse("llevar portátil y cargador")

        assertEquals(IntentType.CARRY_ITEMS, result.intentType)
        assertEquals(
            listOf("portátil", "cargador"),
            result.actions.filter { it.type == ActionType.CARRY }.map { it.label },
        )
    }

    @Test
    fun `llamar a detecta CALL_OR_MESSAGE con el nombre`() = runTest {
        val result = parse("llamar a Marc mañana")

        assertEquals(IntentType.CALL_OR_MESSAGE, result.intentType)
        assertEquals("Marc", result.actions.single { it.type == ActionType.CALL }.label)
    }

    @Test
    fun `recuerdame sin hora genera pregunta de aclaracion y no resuelve time`() = runTest {
        val result = parse("recuérdame llamar al médico")

        assertEquals(IntentType.REMINDER, result.intentType)
        assertEquals(1, result.clarifyingQuestions.size)
        assertEquals("time", result.clarifyingQuestions.single().field)
        assertTrue(result.assistantResponse.startsWith("Aún me falta un dato, señor"))
        assertNull(result.time)
    }

    @Test
    fun `recuerdame con hora y franja explicitas no genera pregunta y resuelve time`() = runTest {
        val result = parse("recuérdame llamar al médico a las 9 de la mañana")

        assertEquals(IntentType.REMINDER, result.intentType)
        assertTrue(result.clarifyingQuestions.isEmpty())
        assertEquals("09:00", result.time)
    }

    @Test
    fun `hora ambigua sin franja no se resuelve y pregunta mañana o tarde`() = runTest {
        val result = parse("recuérdame llamar al médico a las 8")

        assertEquals(IntentType.REMINDER, result.intentType)
        assertNull(result.time)
        assertEquals("time_of_day", result.clarifyingQuestions.single().field)
        assertTrue(result.assistantResponse.startsWith("Aún me falta un dato, señor"))
    }

    @Test
    fun `mañana a las 9 indica el dia pero la hora sigue siendo ambigua`() = runTest {
        val result = parse("avísame mañana a las 9 para llamar al médico")

        assertEquals(IntentType.REMINDER, result.intentType)
        assertEquals("2026-07-03", result.date)
        assertNull(result.time)
        assertEquals("time_of_day", result.clarifyingQuestions.single().field)
    }

    @Test
    fun `avisame mañana a las 9 de la mañana resuelve fecha y hora`() = runTest {
        val result = parse("avísame mañana a las 9 de la mañana para llamar al médico")

        assertEquals(IntentType.REMINDER, result.intentType)
        assertEquals("2026-07-03", result.date)
        assertEquals("09:00", result.time)
        assertTrue(result.clarifyingQuestions.isEmpty())
    }

    @Test
    fun `hoy a las 8 de la mañana no confunde la franja con el dia siguiente`() = runTest {
        val result = parse("recuérdame hoy a las 8 de la mañana ir al médico")

        assertEquals("2026-07-02", result.date)
        assertEquals("08:00", result.time)
        assertTrue(result.clarifyingQuestions.isEmpty())
    }

    @Test
    fun `hoy a las 14 00 resuelve fecha de hoy y hora exacta`() = runTest {
        val result = parse("recuérdame hoy a las 14:00 salir antes")

        assertEquals("2026-07-02", result.date)
        assertEquals("14:00", result.time)
    }

    @Test
    fun `hora en formato de la tarde se convierte a 24h`() = runTest {
        val result = parse("recuérdame hoy a las 2 de la tarde ir al médico")

        assertEquals("14:00", result.time)
    }

    @Test
    fun `sin mencion de dia la fecha queda sin resolver`() = runTest {
        val result = parse("recuérdame a las 9 de la mañana llamar al médico")

        assertNull(result.date)
        assertEquals("09:00", result.time)
    }

    @Test
    fun `oficina sin hora de salida pregunta por ella`() = runTest {
        val result = parse("mañana voy a la oficina")

        assertEquals(IntentType.PREPARE_EVENT, result.intentType)
        assertEquals("departure_time", result.clarifyingQuestions.single().field)
    }

    @Test
    fun `oficina con hora de salida no pregunta`() = runTest {
        val result = parse("mañana voy a la oficina, salgo a las 8")

        assertEquals(IntentType.PREPARE_EVENT, result.intentType)
        assertTrue(result.clarifyingQuestions.isEmpty())
    }

    @Test
    fun `apunta que extrae un hecho memorable`() = runTest {
        val result = parse("Apunta que mi talla de pie es el 42")

        assertEquals(1, result.memoryFacts.size)
        assertEquals("mi talla de pie es el 42", result.memoryFacts.single().fact)
    }

    @Test
    fun `sin disparador de memoria no hay hechos`() = runTest {
        val result = parse("comprar fruta y yogures")

        assertTrue(result.memoryFacts.isEmpty())
    }

    @Test
    fun `buscame cerca detecta NEARBY_SEARCH y limpia la consulta`() = runTest {
        val result = parse("búscame un restaurante vegano cerca")

        assertEquals(IntentType.NEARBY_SEARCH, result.intentType)
        assertEquals("restaurante vegano", result.mapQuery)
    }

    @Test
    fun `busca sin cerca tambien construye la consulta`() = runTest {
        val result = parse("busca una farmacia de guardia")

        assertEquals(IntentType.NEARBY_SEARCH, result.intentType)
        assertEquals("farmacia de guardia", result.mapQuery)
    }

    @Test
    fun `sin disparador de busqueda mapQuery es null`() = runTest {
        val result = parse("comprar fruta y yogures")

        assertNull(result.mapQuery)
    }

    @Test
    fun `cuando llegue a casa crea un recordatorio por lugar sin preguntar hora`() = runTest {
        val result = parse("cuando llegue a casa recuérdame sacar la basura")

        assertEquals(IntentType.REMINDER, result.intentType)
        val draft = result.reminders.single { it.trigger == ReminderTrigger.NEAR_LOCATION }
        assertEquals("casa", draft.place)
        assertTrue(result.clarifyingQuestions.isEmpty())
    }

    @Test
    fun `al llegar al trabajo tambien reconoce el lugar`() = runTest {
        val result = parse("al llegar al trabajo recuérdame fichar")

        val draft = result.reminders.single { it.trigger == ReminderTrigger.NEAR_LOCATION }
        assertEquals("trabajo", draft.place)
    }

    @Test
    fun `una pregunta con interrogante final se clasifica como QUERY sin efectos`() = runTest {
        val result = parse("¿qué tengo que hacer mañana?")

        assertEquals(IntentType.QUERY, result.intentType)
        assertTrue(result.actions.isEmpty())
        assertTrue(result.reminders.isEmpty())
        assertTrue(result.clarifyingQuestions.isEmpty())
    }

    @Test
    fun `una pregunta con comprar no dispara SHOPPING sino QUERY`() = runTest {
        val result = parse("qué me queda por comprar")

        assertEquals(IntentType.QUERY, result.intentType)
        assertTrue(result.actions.isEmpty())
    }

    @Test
    fun `una orden de recordatorio no se confunde con una pregunta`() = runTest {
        val result = parse("recuérdame llamar al médico a las 9 de la mañana")

        assertEquals(IntentType.REMINDER, result.intentType)
    }

    @Test
    fun `borrala se clasifica como DELETE sin extraer objetivo`() = runTest {
        val result = parse("bórrala")

        assertEquals(IntentType.DELETE, result.intentType)
        assertNull(result.deleteQuery)
    }

    @Test
    fun `elimina X se clasifica como DELETE con el objetivo`() = runTest {
        val result = parse("elimina el recordatorio del médico")

        assertEquals(IntentType.DELETE, result.intentType)
        assertEquals("el recordatorio del médico", result.deleteQuery)
    }

    @Test
    fun `texto sin disparadores es UNKNOWN`() = runTest {
        val result = parse("hola qué tal todo bien")

        assertEquals(IntentType.UNKNOWN, result.intentType)
        assertTrue(result.confidence < 0.5f)
    }

    @Test
    fun `mood detecta MOOD_OR_ENERGY`() = runTest {
        val result = parse("estoy muy cansado hoy")

        assertEquals(IntentType.MOOD_OR_ENERGY, result.intentType)
    }

    @Test
    fun `el ejemplo del prompt de producto genera acciones de llevar y comprar`() = runTest {
        val result = parse(
            "Mañana voy a la oficina, acuérdame llevar el portátil y el cargador, " +
                "y al volver comprar fruta y yogures.",
        )

        val carryLabels = result.actions.filter { it.type == ActionType.CARRY }.map { it.label }
        val buyLabels = result.actions.filter { it.type == ActionType.BUY }.map { it.label }
        assertTrue(carryLabels.containsAll(listOf("el portátil", "el cargador")))
        assertTrue(buyLabels.containsAll(listOf("fruta", "yogures")))
    }
}
