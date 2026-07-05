package com.nehonar.operator.core.ai

sealed interface AIParseResult {
    data class Success(val intent: ParsedIntent) : AIParseResult
    data class Failure(val reason: String) : AIParseResult
}

/** Un turno previo de la conversación, para dar contexto a la IA (resolver "bórrala"…). */
data class PriorMessage(
    val fromUser: Boolean,
    val text: String,
)

interface AIProvider {
    /**
     * Interpreta [transcript]. [history] son los últimos turnos de la conversación
     * (usuario/operador), para resolver referencias sin contexto explícito. Vacío
     * en flujos de una sola nota (captura/revisión).
     */
    suspend fun parseVoiceNote(
        transcript: String,
        history: List<PriorMessage> = emptyList(),
    ): AIParseResult
}
