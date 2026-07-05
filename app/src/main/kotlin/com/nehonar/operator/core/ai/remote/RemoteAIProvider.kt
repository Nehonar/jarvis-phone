package com.nehonar.operator.core.ai.remote

import com.nehonar.operator.core.ai.AIParseResult
import com.nehonar.operator.core.ai.AIProvider
import com.nehonar.operator.core.calendar.CalendarRepository
import com.nehonar.operator.core.common.TimeProvider
import com.nehonar.operator.core.common.formatOperatorTime
import com.nehonar.operator.core.domain.repository.ChecklistRepository
import com.nehonar.operator.core.domain.repository.MemoryRepository
import com.nehonar.operator.core.domain.repository.PlaceRepository
import com.nehonar.operator.core.domain.repository.ReminderRepository
import java.io.IOException
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Cliente único para proveedores compatibles con el formato "chat completions" de
 * OpenAI (DeepSeek, OpenAI, Gemini vía su capa de compatibilidad). Cambiar de
 * proveedor es cambiar [RemoteAIProviderConfig], no código.
 */
class RemoteAIProvider(
    private val config: RemoteAIProviderConfig,
    private val httpClient: OkHttpClient,
    private val timeProvider: TimeProvider,
    private val calendarRepository: CalendarRepository,
    private val memoryRepository: MemoryRepository,
    private val placeRepository: PlaceRepository,
    private val reminderRepository: ReminderRepository,
    private val checklistRepository: ChecklistRepository,
) : AIProvider {

    override suspend fun parseVoiceNote(transcript: String): AIParseResult = withContext(Dispatchers.IO) {
        if (config.apiKey.isBlank()) {
            return@withContext AIParseResult.Failure("Falta configurar la clave de ${config.displayName}")
        }

        val zone = ZoneId.systemDefault()
        val today = timeProvider.today()
        val agenda = calendarRepository.getEventsForDays(AGENDA_DAYS).map { event ->
            val dayTag = when (event.startAt.atZone(zone).toLocalDate()) {
                today -> "HOY"
                today.plusDays(1) -> "MAÑANA"
                else -> event.startAt.atZone(zone).toLocalDate().toString()
            }
            if (event.allDay) "$dayTag (todo el día) ${event.title}"
            else "$dayTag ${formatOperatorTime(event.startAt, zone)} ${event.title}"
        }
        val memoryFacts = memoryRepository.getRecent(MEMORY_FACTS_IN_PROMPT)
            .map { "${it.topic}: ${it.fact}" }
        val places = placeRepository.getAll().map { it.label }
        val currentState = buildCurrentState(zone, today)
        val requestJson = json.encodeToString(
            ChatCompletionRequest.serializer(),
            ChatCompletionRequest(
                model = config.model,
                messages = listOf(
                    ChatMessage(
                        role = "system",
                        content = PromptBuilder.systemPrompt(today, agenda, memoryFacts, places, currentState),
                    ),
                    ChatMessage(role = "user", content = PromptBuilder.buildUserMessage(transcript)),
                ),
            ),
        )

        val request = Request.Builder()
            .url("${config.baseUrl}/chat/completions")
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .post(requestJson.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext AIParseResult.Failure(response.code.toFailureReason())
                }
                val rawBody = response.body?.string().orEmpty()
                val content = extractContent(rawBody)
                    ?: return@withContext AIParseResult.Failure("Respuesta no válida")
                val intent = IntentJsonParser.parse(content)
                    ?: return@withContext AIParseResult.Failure("Respuesta no válida")
                AIParseResult.Success(intent)
            }
        } catch (e: IOException) {
            AIParseResult.Failure("Sin conexión")
        }
    }

    /** Recordatorios pendientes y checklist abierta, para que la IA responda preguntas. */
    private suspend fun buildCurrentState(zone: ZoneId, today: java.time.LocalDate): List<String> = buildList {
        reminderRepository.getAllPending().take(STATE_ITEMS).forEach { reminder ->
            val date = reminder.triggerAt.atZone(zone).toLocalDate()
            val dayTag = when (date) {
                today -> "HOY"
                today.plusDays(1) -> "MAÑANA"
                else -> date.toString()
            }
            add("Recordatorio $dayTag ${formatOperatorTime(reminder.triggerAt, zone)} — ${reminder.message}")
        }
        checklistRepository.observeAll().first()
            .filter { !it.done }
            .take(STATE_ITEMS)
            .forEach { add("Pendiente (${it.type.name}): ${it.label}") }
    }

    private fun extractContent(rawBody: String): String? = try {
        json.decodeFromString(ChatCompletionResponse.serializer(), rawBody)
            .choices.firstOrNull()?.message?.content
    } catch (e: SerializationException) {
        null
    }

    private fun Int.toFailureReason(): String = when (this) {
        401, 403 -> "Clave inválida"
        429 -> "Límite de uso alcanzado"
        in 500..599 -> "Servicio no disponible"
        else -> "Error de red ($this)"
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        const val MEMORY_FACTS_IN_PROMPT = 50
        const val STATE_ITEMS = 20
        const val AGENDA_DAYS = 2
    }
}
