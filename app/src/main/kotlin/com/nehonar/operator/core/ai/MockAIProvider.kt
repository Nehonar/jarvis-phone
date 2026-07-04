package com.nehonar.operator.core.ai

import com.nehonar.operator.core.common.TimeProvider
import java.time.LocalDate
import javax.inject.Inject

/**
 * Proveedor de IA simulado: reglas deterministas por palabra clave, sin red.
 * Sirve de contrato estable para el resto de la app hasta que la Fase 3
 * conecte un proveedor real detrás de la misma interfaz [AIProvider].
 */
class MockAIProvider @Inject constructor(
    private val timeProvider: TimeProvider,
) : AIProvider {

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

        val memoryFacts = extractMemoryFacts(lower, text)
        val resolvedDate = extractDate(lower, timeProvider.today())
        val timeExtraction = extractTime(lower)
        val resolvedTime = (timeExtraction as? TimeExtraction.Resolved)?.time
        val hasDepartureTime = DEPARTURE_REGEX.containsMatchIn(lower)

        val intentType = classify(lower)
        val clarifyingQuestions = buildList {
            if (intentType == IntentType.REMINDER && timeExtraction is TimeExtraction.None) {
                add(ClarifyingQuestion("time", "¿A qué hora?"))
            }
            if (intentType == IntentType.REMINDER && timeExtraction is TimeExtraction.Ambiguous) {
                add(ClarifyingQuestion("time_of_day", "¿De la mañana o de la tarde?"))
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
            if (intentType == IntentType.REMINDER && resolvedTime != null) {
                add(ReminderDraft(trigger = ReminderTrigger.EXACT_TIME, message = text))
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
                date = resolvedDate,
                time = resolvedTime,
                memoryFacts = memoryFacts,
            ),
        )
    }

    /** "apunta que X" / "recuerda que X" ⇒ hecho memorable con el texto tal cual. */
    private fun extractMemoryFacts(lower: String, original: String): List<MemoryFactDraft> {
        val matchEnd = MEMORY_TRIGGERS.firstNotNullOfOrNull { wordBoundaryRegex(it).find(lower)?.range?.last }
            ?: return emptyList()
        val fact = original.substring((matchEnd + 1).coerceAtMost(original.length))
            .trim().trim('.', '!', '?')
        if (fact.isEmpty()) return emptyList()
        return listOf(MemoryFactDraft(topic = fact.take(24), fact = fact))
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

    /** Solo entiende "hoy"/"mañana"; no resuelve días de la semana ni fechas explícitas. */
    private fun extractDate(lower: String, today: LocalDate): String? {
        // "de/por la mañana" es franja horaria, no fecha: se quita antes de buscar "mañana".
        val withoutTimeOfDay = TIME_OF_DAY_MORNING_REGEX.replace(lower, " ")
        return when {
            wordBoundaryRegex("mañana").containsMatchIn(withoutTimeOfDay) -> today.plusDays(1).toString()
            wordBoundaryRegex("hoy").containsMatchIn(withoutTimeOfDay) -> today.toString()
            else -> null
        }
    }

    private sealed interface TimeExtraction {
        /** La nota no menciona ninguna hora. */
        data object None : TimeExtraction

        /** Hora 1-11 sin franja (mañana/tarde/noche): no se adivina, se pregunta (D-013). */
        data object Ambiguous : TimeExtraction

        data class Resolved(val time: String) : TimeExtraction
    }

    /**
     * Solo entiende horas en dígitos ("a las 8", "a las 14:00", "8:30"); no resuelve
     * números escritos en palabras ("las dos") — limitación aceptada del mock (ver
     * riesgo 3 en docs/fase-4-plan.md). Reconoce la franja ("de la mañana", "de la
     * tarde"…) para pasar a 24h; sin franja, una hora 1-11 es ambigua y no se resuelve.
     */
    private fun extractTime(lower: String): TimeExtraction {
        val match = TIME_REGEX.find(lower) ?: return TimeExtraction.None
        val hourStr = match.groupValues[1].ifEmpty { match.groupValues[3] }
        val minuteStr = match.groupValues[2].ifEmpty { match.groupValues[4] }
        var hour = hourStr.toIntOrNull() ?: return TimeExtraction.None
        val minute = minuteStr.toIntOrNull() ?: 0
        if (hour !in 0..23 || minute !in 0..59) return TimeExtraction.None
        if (hour in 1..11) {
            val tail = lower.substring((match.range.last + 1).coerceAtMost(lower.length)).take(24)
            when {
                PM_MARKERS.any { tail.contains(it) } -> hour += 12
                AM_MARKERS.none { tail.contains(it) } -> return TimeExtraction.Ambiguous
            }
        }
        return TimeExtraction.Resolved("%02d:%02d".format(hour, minute))
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
        val REMINDER_TRIGGERS = listOf("recuérdame", "recuerdame", "avísame", "avisame", "acuérdame", "acuerdame")
        val MEMORY_TRIGGERS = listOf("apunta que", "recuerda que")
        val PREPARE_EVENT_TRIGGERS = listOf("oficina", "gimnasio", "viaje")
        val MOOD_TRIGGERS = listOf("cansado", "cansada", "sin energía", "sin energia", "baja energía", "baja energia")
        val PM_MARKERS = listOf("mediodía", "mediodia", "tarde", "noche")
        // Frase completa: "mañana" a secas tras la hora es el día siguiente, no la franja.
        val AM_MARKERS = listOf(
            "de la mañana", "de la manana",
            "por la mañana", "por la manana",
            "de la madrugada",
        )
        val TIME_OF_DAY_MORNING_REGEX = Regex("""\b(de|por) la (mañana|manana|madrugada)\b""")

        val STOP_WORDS_REGEX = Regex(
            "\\b(y al |y luego |luego |mañana |también |tambien )\\b",
            RegexOption.IGNORE_CASE,
        )
        // "a las 9", "a las 14:00", "9:30" — captura hora en (1)/(3) y minutos en (2)/(4).
        val TIME_REGEX = Regex("""\ba las (\d{1,2})(?::(\d{2}))?\b|\b(\d{1,2}):(\d{2})\b""")
        // Detecta que ya se indicó la hora de salida: "salgo/salir/sales ... 8"
        val DEPARTURE_REGEX = Regex("""\bsal(go|ir|es)\b[^.]{0,20}\d{1,2}""")
    }
}
