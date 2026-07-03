package com.nehonar.operator.core.ai.remote

import com.nehonar.operator.core.ai.ActionItem
import com.nehonar.operator.core.ai.ActionType
import com.nehonar.operator.core.ai.ClarifyingQuestion
import com.nehonar.operator.core.ai.IntentType
import com.nehonar.operator.core.ai.ParsedIntent
import com.nehonar.operator.core.ai.Priority
import com.nehonar.operator.core.ai.ReminderDraft
import com.nehonar.operator.core.ai.ReminderTrigger
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Convierte la respuesta cruda de la IA (texto, posiblemente envuelto en fences de
 * markdown) en un [ParsedIntent]. Función pura, sin IO: testeable sin red.
 * Devuelve `null` si el contenido no es un JSON válido según nuestro contrato.
 */
object IntentJsonParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun parse(rawContent: String): ParsedIntent? {
        val cleaned = stripMarkdownFences(rawContent)
        if (cleaned.isBlank()) return null
        val dto = try {
            json.decodeFromString(IntentJson.serializer(), cleaned)
        } catch (e: SerializationException) {
            return null
        } catch (e: IllegalArgumentException) {
            return null
        }
        if (dto.assistant_response.isBlank()) return null
        return dto.toDomain()
    }

    private fun stripMarkdownFences(raw: String): String {
        var s = raw.trim()
        if (s.startsWith("```")) {
            s = s.removePrefix("```json").removePrefix("```JSON").removePrefix("```").trim()
            if (s.endsWith("```")) {
                s = s.removeSuffix("```").trim()
            }
        }
        return s
    }

    private fun IntentJson.toDomain(): ParsedIntent = ParsedIntent(
        intentType = IntentType.entries.firstOrNull { it.name == intent_type.uppercase() } ?: IntentType.UNKNOWN,
        confidence = confidence.coerceIn(0f, 1f),
        title = title,
        summary = summary,
        actions = actions.map { it.toDomain() },
        reminders = reminders.map { it.toDomain() },
        clarifyingQuestions = clarifying_questions.map { ClarifyingQuestion(it.field, it.question) },
        assistantResponse = assistant_response,
        needsConfirmation = needs_confirmation,
    )

    private fun ActionJson.toDomain(): ActionItem = ActionItem(
        type = ActionType.entries.firstOrNull { it.name == type.uppercase() } ?: ActionType.OTHER,
        label = label,
        priority = Priority.entries.firstOrNull { it.name == priority.uppercase() } ?: Priority.MEDIUM,
    )

    private fun ReminderJson.toDomain(): ReminderDraft = ReminderDraft(
        trigger = ReminderTrigger.entries.firstOrNull { it.name == trigger.uppercase() } ?: ReminderTrigger.NONE,
        message = message,
    )
}
