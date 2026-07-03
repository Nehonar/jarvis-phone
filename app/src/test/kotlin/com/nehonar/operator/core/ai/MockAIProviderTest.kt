package com.nehonar.operator.core.ai

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class MockAIProviderTest {

    private val provider = MockAIProvider()

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
    fun `recuerdame sin hora genera pregunta de aclaracion`() = runTest {
        val result = parse("recuérdame llamar al médico")

        assertEquals(IntentType.REMINDER, result.intentType)
        assertEquals(1, result.clarifyingQuestions.size)
        assertEquals("time", result.clarifyingQuestions.single().field)
        assertTrue(result.assistantResponse.startsWith("Falta dato"))
    }

    @Test
    fun `recuerdame con hora explicita no genera pregunta`() = runTest {
        val result = parse("recuérdame llamar al médico a las 9")

        assertEquals(IntentType.REMINDER, result.intentType)
        assertTrue(result.clarifyingQuestions.isEmpty())
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
