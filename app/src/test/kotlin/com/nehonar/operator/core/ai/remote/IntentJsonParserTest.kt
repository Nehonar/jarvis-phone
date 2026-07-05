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

    @Test
    fun `date y time validos se conservan`() {
        val json = """
            {"intent_type": "REMINDER", "assistant_response": "ok", "date": "2026-07-04", "time": "09:30"}
        """.trimIndent()

        val intent = IntentJsonParser.parse(json)

        requireNotNull(intent)
        assertEquals("2026-07-04", intent.date)
        assertEquals("09:30", intent.time)
    }

    @Test
    fun `date con formato invalido se descarta en vez de romper`() {
        val json = """
            {"intent_type": "REMINDER", "assistant_response": "ok", "date": "mañana", "time": "09:30"}
        """.trimIndent()

        val intent = IntentJsonParser.parse(json)

        requireNotNull(intent)
        assertNull(intent.date)
        assertEquals("09:30", intent.time)
    }

    @Test
    fun `time con formato invalido se descarta en vez de romper`() {
        val json = """
            {"intent_type": "REMINDER", "assistant_response": "ok", "date": "2026-07-04", "time": "a las nueve"}
        """.trimIndent()

        val intent = IntentJsonParser.parse(json)

        requireNotNull(intent)
        assertEquals("2026-07-04", intent.date)
        assertNull(intent.time)
    }

    @Test
    fun `memory_facts se parsean y los vacios se descartan`() {
        val json = """
            {"intent_type": "GENERAL_NOTE", "assistant_response": "ok",
             "memory_facts": [
               {"topic": "talla de pie", "fact": "El usuario calza un 42"},
               {"topic": "vacio", "fact": "   "},
               {"fact": "A Marc le gusta el vino tinto"}
             ]}
        """.trimIndent()

        val intent = IntentJsonParser.parse(json)

        requireNotNull(intent)
        assertEquals(2, intent.memoryFacts.size)
        assertEquals("talla de pie", intent.memoryFacts.first().topic)
        // Sin topic explícito cae al genérico.
        assertEquals("nota", intent.memoryFacts.last().topic)
    }

    @Test
    fun `intent_type QUERY se mapea y conserva la respuesta`() {
        val json = """
            {"intent_type": "QUERY", "assistant_response": "Mañana tiene la revisión médica a las 08:00, señor."}
        """.trimIndent()

        val intent = IntentJsonParser.parse(json)

        requireNotNull(intent)
        assertEquals(IntentType.QUERY, intent.intentType)
        assertEquals("Mañana tiene la revisión médica a las 08:00, señor.", intent.assistantResponse)
    }

    @Test
    fun `intent_type DELETE con delete_query se mapea`() {
        val json = """
            {"intent_type": "DELETE", "assistant_response": "Voy a buscarlo, señor.",
             "delete_query": "  recordatorio del médico  "}
        """.trimIndent()

        val intent = IntentJsonParser.parse(json)

        requireNotNull(intent)
        assertEquals(IntentType.DELETE, intent.intentType)
        assertEquals("recordatorio del médico", intent.deleteQuery)
    }

    @Test
    fun `map_query se parsea para NEARBY_SEARCH`() {
        val json = """
            {"intent_type": "NEARBY_SEARCH", "assistant_response": "ok",
             "map_query": "  restaurante vegano  "}
        """.trimIndent()

        val intent = IntentJsonParser.parse(json)

        requireNotNull(intent)
        assertEquals(IntentType.NEARBY_SEARCH, intent.intentType)
        assertEquals("restaurante vegano", intent.mapQuery)
    }

    @Test
    fun `reminder con place se parsea para NEAR_LOCATION`() {
        val json = """
            {"intent_type": "REMINDER", "assistant_response": "ok",
             "reminders": [{"trigger": "NEAR_LOCATION", "message": "Sacar la basura", "place": "casa"}]}
        """.trimIndent()

        val intent = IntentJsonParser.parse(json)

        requireNotNull(intent)
        val reminder = intent.reminders.single()
        assertEquals("casa", reminder.place)
    }

    @Test
    fun `map_query en blanco o ausente queda null`() {
        val blank = IntentJsonParser.parse(
            """{"intent_type": "GENERAL_NOTE", "assistant_response": "ok", "map_query": "   "}""",
        )
        val absent = IntentJsonParser.parse(
            """{"intent_type": "SHOPPING", "assistant_response": "ok"}""",
        )

        assertNull(blank?.mapQuery)
        assertNull(absent?.mapQuery)
    }

    @Test
    fun `sin memory_facts la lista queda vacia`() {
        val json = """{"intent_type": "SHOPPING", "assistant_response": "ok"}"""

        val intent = IntentJsonParser.parse(json)

        requireNotNull(intent)
        assertTrue(intent.memoryFacts.isEmpty())
    }

    @Test
    fun `date y time ausentes son null por defecto`() {
        val json = """{"intent_type": "SHOPPING", "assistant_response": "ok"}"""

        val intent = IntentJsonParser.parse(json)

        requireNotNull(intent)
        assertNull(intent.date)
        assertNull(intent.time)
    }
}
