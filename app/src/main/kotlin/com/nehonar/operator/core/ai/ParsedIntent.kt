package com.nehonar.operator.core.ai

import kotlinx.serialization.Serializable

@Serializable
enum class IntentType {
    REMINDER,
    PREPARE_EVENT,
    CARRY_ITEMS,
    SHOPPING,
    CALL_OR_MESSAGE,
    MOOD_OR_ENERGY,
    IDEA_CAPTURE,
    DAILY_CONSTRAINT,
    GENERAL_NOTE,
    UNKNOWN,
}

@Serializable
enum class ActionType {
    CARRY,
    BUY,
    CALL,
    PREPARE,
    REMEMBER,
    NOTE,
    OTHER,
}

@Serializable
enum class Priority {
    LOW,
    MEDIUM,
    HIGH,
}

@Serializable
enum class ReminderTrigger {
    BEFORE_EVENT,
    BEFORE_LEAVING_HOME,
    NEAR_LOCATION,
    FREE_WINDOW,
    EXACT_TIME,
    NONE,
}

@Serializable
data class ClarifyingQuestion(
    val field: String,
    val question: String,
)

@Serializable
data class ActionItem(
    val type: ActionType,
    val label: String,
    val priority: Priority = Priority.MEDIUM,
)

@Serializable
data class ReminderDraft(
    val trigger: ReminderTrigger,
    val message: String,
)

/** Hecho personal estable detectado en la nota; se persiste solo al aceptar. */
@Serializable
data class MemoryFactDraft(
    /** Etiqueta corta para listar, p. ej. "talla de pie". */
    val topic: String,
    /** Enunciado autocontenido, p. ej. "El usuario calza un 42". */
    val fact: String,
)

@Serializable
data class ParsedIntent(
    val intentType: IntentType,
    val confidence: Float,
    val title: String,
    val summary: String,
    val actions: List<ActionItem> = emptyList(),
    val reminders: List<ReminderDraft> = emptyList(),
    val clarifyingQuestions: List<ClarifyingQuestion> = emptyList(),
    val assistantResponse: String,
    val needsConfirmation: Boolean = true,
    /** ISO "YYYY-MM-DD", ya resuelta (sin "mañana"/"hoy" literal). Null si no aplica. */
    val date: String? = null,
    /** "HH:mm" en 24h. Null si no aplica. */
    val time: String? = null,
    val memoryFacts: List<MemoryFactDraft> = emptyList(),
)
