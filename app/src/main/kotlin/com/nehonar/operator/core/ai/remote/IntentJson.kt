package com.nehonar.operator.core.ai.remote

import kotlinx.serialization.Serializable

/** Forma exacta que debe devolver la IA. Ver PromptBuilder para las instrucciones. */
@Serializable
data class IntentJson(
    val intent_type: String,
    val confidence: Float = 0.5f,
    val title: String = "",
    val summary: String = "",
    val actions: List<ActionJson> = emptyList(),
    val reminders: List<ReminderJson> = emptyList(),
    val clarifying_questions: List<ClarifyingQuestionJson> = emptyList(),
    val assistant_response: String,
    val needs_confirmation: Boolean = true,
    /** ISO "YYYY-MM-DD", ya resuelta por la IA (nunca "mañana" literal). */
    val date: String? = null,
    /** "HH:mm" en 24h. */
    val time: String? = null,
    val memory_facts: List<MemoryFactJson> = emptyList(),
)

@Serializable
data class ActionJson(
    val type: String = "OTHER",
    val label: String,
    val priority: String = "MEDIUM",
)

@Serializable
data class ReminderJson(
    val trigger: String = "NONE",
    val message: String,
)

@Serializable
data class ClarifyingQuestionJson(
    val field: String,
    val question: String,
)

@Serializable
data class MemoryFactJson(
    val topic: String = "",
    val fact: String,
)
