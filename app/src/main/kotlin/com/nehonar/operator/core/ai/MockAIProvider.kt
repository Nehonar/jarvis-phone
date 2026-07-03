package com.nehonar.operator.core.ai

import javax.inject.Inject

/**
 * Proveedor de IA simulado: reglas deterministas por palabra clave, sin red.
 * Sirve de contrato estable para el resto de la app hasta que la Fase 3
 * conecte un proveedor real detrás de la misma interfaz [AIProvider].
 */
class MockAIProvider @Inject constructor() : AIProvider {

    override suspend fun parseVoiceNote(transcript: String): AIParseResult {
        val text = transcript.trim()
        val lower = text.lowercase()

        val buyItems = extractItemsAfterTrigger(lower, text, BUY_TRIGGERS)
        val carryItems = extractItemsAfterTrigger(lower, text, CARRY_TRIGGERS)
        val callTarget = extractCallTarget(lower, text)

        val actions = buildList {
            buyItems.forEach { add(ActionItem(ActionType.BUY, it, Priority.MEDIUM)) }
            carryItems.forEach { add(ActionItem(ActionType.CARRY, it, Priority.HIGH)) }
            callTarget?.let { add(ActionItem(ActionType.CALL, it, Priority.MEDIUM)) }
        }

        val hasTime = TIME_REGEX.containsMatchIn(lower)
        val hasDepartureTime = DEPARTURE_REGEX.containsMatchIn(lower)

        val intentType = classify(lower)
        val clarifyingQuestions = buildList {
            if (intentType == IntentType.REMINDER && !hasTime) {
                add(ClarifyingQuestion("time", "¿A qué hora?"))
            }
            if (intentType == IntentType.PREPARE_EVENT && !hasDepartureTime) {
                add(ClarifyingQuestion("departure_time", "¿A qué hora sales de casa?"))
            }
        }

        val reminders = buildList {
            if (carryItems.isNotEmpty()) {
                add(
                    ReminderDraft(
                        trigger = ReminderTrigger.BEFORE_LEAVING_HOME,
                        message = "Recuerda: ${carryItems.joinToString(", ")}.",
                    ),
                )
            }
            if (buyItems.isNotEmpty()) {
                add(
                    ReminderDraft(
                        trigger = ReminderTrigger.FREE_WINDOW,
                        message = "Recado pendiente: comprar ${buyItems.joinToString(", ")}.",
                    ),
                )
            }
        }

        return AIParseResult.Success(
            ParsedIntent(
                intentType = intentType,
                confidence = if (intentType == IntentType.UNKNOWN) 0.2f else 0.8f,
                title = intentType.toTitle(),
                summary = text,
                actions = actions,
                reminders = reminders,
                clarifyingQuestions = clarifyingQuestions,
                assistantResponse = buildAssistantResponse(intentType, actions, clarifyingQuestions),
                needsConfirmation = true,
            ),
        )
    }

    private fun classify(lower: String): IntentType = when {
        matchesAny(lower, REMINDER_TRIGGERS) -> IntentType.REMINDER
        matchesAny(lower, PREPARE_EVENT_TRIGGERS) -> IntentType.PREPARE_EVENT
        matchesAny(lower, BUY_TRIGGERS) -> IntentType.SHOPPING
        matchesAny(lower, CARRY_TRIGGERS) -> IntentType.CARRY_ITEMS
        matchesAny(lower, CALL_TRIGGERS) -> IntentType.CALL_OR_MESSAGE
        matchesAny(lower, MOOD_TRIGGERS) -> IntentType.MOOD_OR_ENERGY
        else -> IntentType.UNKNOWN
    }

    private fun matchesAny(lower: String, triggers: List<String>): Boolean =
        triggers.any { wordBoundaryRegex(it).containsMatchIn(lower) }

    /** Coincide con `trigger` como frase completa (límites de palabra en ambos extremos). */
    private fun wordBoundaryRegex(trigger: String): Regex = Regex("\\b${Regex.escape(trigger)}\\b")

    private fun extractItemsAfterTrigger(lower: String, original: String, triggers: List<String>): List<String> {
        val matchEnd = triggers.firstNotNullOfOrNull { wordBoundaryRegex(it).find(lower)?.range?.last }
            ?: return emptyList()
        val startIndex = matchEnd + 1
        val rest = original.substring(startIndex.coerceAtMost(original.length))
        val stopIndex = STOP_WORDS_REGEX.find(rest)?.range?.first ?: rest.length
        val segment = rest.substring(0, stopIndex)
        return segment.split(",", " y ")
            .map { it.trim().trim('.', '!', '?') }
            .filter { it.isNotEmpty() }
    }

    private fun extractCallTarget(lower: String, original: String): String? {
        val matchEnd = CALL_TRIGGERS.firstNotNullOfOrNull { wordBoundaryRegex(it).find(lower)?.range?.last }
            ?: return null
        val startIndex = matchEnd + 1
        val rest = original.substring(startIndex.coerceAtMost(original.length)).trim()
        // Un nombre propio es normalmente una sola palabra; evita arrastrar el resto de la frase.
        return rest.takeWhile { it.isLetter() }.takeIf { it.isNotEmpty() }
    }

    // Tono: mayordomo distinguido, seco, servicial y con una pizca de sarcasmo.
    // Se dirige al usuario como "señor". Nada de personajes protegidos: es un estilo
    // genérico, no una imitación de ninguna voz o actor concreto (ver D-009).
    private fun buildAssistantResponse(
        intentType: IntentType,
        actions: List<ActionItem>,
        clarifyingQuestions: List<ClarifyingQuestion>,
    ): String {
        clarifyingQuestions.firstOrNull()?.let { return "Aún me falta un dato, señor: ${it.question}" }
        return when (intentType) {
            IntentType.SHOPPING -> "Recado de compra anotado, señor."
            IntentType.CARRY_ITEMS ->
                "Lista preparada, señor: ${actions.joinToString(", ") { it.label }}. Procure no olvidarla."
            IntentType.CALL_OR_MESSAGE -> "Contacto pendiente registrado, señor."
            IntentType.REMINDER -> "Recordatorio programado, señor. Yo me acordaré, ya que usted no lo hará."
            IntentType.PREPARE_EVENT -> "Modo preparación activado, señor."
            IntentType.MOOD_OR_ENERGY -> "Estado anímico registrado, señor. Ajustaré mis expectativas."
            IntentType.IDEA_CAPTURE -> "Idea guardada, señor. Confío en que mejore con el tiempo."
            IntentType.DAILY_CONSTRAINT -> "Restricción registrada, señor."
            IntentType.GENERAL_NOTE -> "Nota guardada, señor."
            IntentType.UNKNOWN -> "No he identificado ninguna acción clara, señor. Quizás con más detalle."
        }
    }

    private fun IntentType.toTitle(): String = name.replace('_', ' ')

    private companion object {
        val BUY_TRIGGERS = listOf("comprar")
        val CARRY_TRIGGERS = listOf("llevar")
        val CALL_TRIGGERS = listOf("llamar a", "avisar a")
        val REMINDER_TRIGGERS = listOf("recuérdame", "recuerdame")
        val PREPARE_EVENT_TRIGGERS = listOf("oficina", "gimnasio", "viaje")
        val MOOD_TRIGGERS = listOf("cansado", "cansada", "sin energía", "sin energia", "baja energía", "baja energia")

        val STOP_WORDS_REGEX = Regex(
            "\\b(y al |y luego |luego |mañana |también |tambien )\\b",
            RegexOption.IGNORE_CASE,
        )
        // Detecta una hora explícita: "a las 9", "9:30", "9h"
        val TIME_REGEX = Regex("""\ba las \d{1,2}\b|\b\d{1,2}[:h]\d{2}\b""")
        // Detecta que ya se indicó la hora de salida: "salgo/salir/sales ... 8"
        val DEPARTURE_REGEX = Regex("""\bsal(go|ir|es)\b[^.]{0,20}\d{1,2}""")
    }
}
