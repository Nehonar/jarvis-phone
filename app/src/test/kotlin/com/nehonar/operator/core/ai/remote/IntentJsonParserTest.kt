package com.nehonar.operator.core.ai.remote

import com.nehonar.operator.core.ai.ActionType
import com.nehonar.operator.core.ai.IntentType
import com.nehonar.operator.core.ai.Priority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IntentJsonParserTest {

    private val validJson = """
        {
          "intent_type": "SHOPPING",
          "confidence": 0.9,
          "title": "Compra",
          "summary": "comprar fruta",
          "actions": [{"type": "BUY", "label": "fruta", "priority": "MEDIUM"}],
          "reminders": [{"trigger": "FREE_WINDOW", "message": "Recado pendiente: fruta."}],
          "clarifying_questions": [],
          "assistant_response": "Recado detectado: compra pendiente.",
          "needs_confirmation": true
        }
    """.trimIndent()

    @Test
    fun `parsea JSON valido`() {
        val intent = IntentJsonParser.parse(validJson)

        requireNotNull(intent)
        assertEquals(IntentType.SHOPPING, intent.intentType)
        assertEquals(0.9f, intent.confidence, 0.001f)
        assertEquals("fruta", intent.actions.single().label)
        assertEquals(ActionType.BUY, intent.actions.single().type)
        assertEquals(Priority.MEDIUM, intent.actions.single().priority)
        assertEquals("Recado detectado: compra pendiente.", intent.assistantResponse)
    }

    @Test
    fun `quita fences de markdown antes de parsear`() {
        val wrapped = "```json\n$validJson\n```"

        val intent = IntentJsonParser.parse(wrapped)

        requireNotNull(intent)
        assertEquals(IntentType.SHOPPING, intent.intentType)
    }

    @Test
    fun `fences de markdown sin la palabra json tambien se aceptan`() {
        val wrapped = "```\n$validJson\n```"

        val intent = IntentJsonParser.parse(wrapped)

        requireNotNull(intent)
        assertEquals(IntentType.SHOPPING, intent.intentType)
    }

    @Test
    fun `campo assistant_response en blanco se rechaza`() {
        val json = """{"intent_type": "SHOPPING", "assistant_response": "   "}"""

        assertNull(IntentJsonParser.parse(json))
    }

    @Test
    fun `falta assistant_response obligatorio devuelve null`() {
        val json = """{"intent_type": "SHOPPING"}"""

        assertNull(IntentJsonParser.parse(json))
    }

    @Test
    fun `json corrupto devuelve null en vez de lanzar`() {
        assertNull(IntentJsonParser.parse("esto no es json"))
        assertNull(IntentJsonParser.parse(""))
        assertNull(IntentJsonParser.parse("{ \"intent_type\": "))
    }

    @Test
    fun `intent_type desconocido cae a UNKNOWN sin fallar`() {
        val json = """{"intent_type": "ALGO_RARO_NO_EXISTENTE", "assistant_response": "ok"}"""

        val intent = IntentJsonParser.parse(json)

        requireNotNull(intent)
        assertEquals(IntentType.UNKNOWN, intent.intentType)
    }

    @Test
    fun `confidence fuera de rango se recorta a 0 a 1`() {
        val json = """{"intent_type": "SHOPPING", "confidence": 5.0, "assistant_response": "ok"}"""

        val intent = IntentJsonParser.parse(json)

        requireNotNull(intent)
        assertTrue(intent.confidence <= 1f)
    }

    @Test
    fun `campos opcionales ausentes usan los valores por defecto`() {
        val json = """{"intent_type": "UNKNOWN", "assistant_response": "No se ha identificado una acción clara."}"""

        val intent = IntentJsonParser.parse(json)

        requireNotNull(intent)
        assertTrue(intent.actions.isEmpty())
        assertTrue(intent.reminders.isEmpty())
        assertTrue(intent.clarifyingQuestions.isEmpty())
    }
}
