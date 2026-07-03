package com.nehonar.operator.core.ai.remote

import com.nehonar.operator.core.ai.AIParseResult
import com.nehonar.operator.core.ai.AIProvider
import com.nehonar.operator.core.common.TimeProvider
import java.io.IOException
import kotlinx.coroutines.Dispatchers
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
) : AIProvider {

    override suspend fun parseVoiceNote(transcript: String): AIParseResult = withContext(Dispatchers.IO) {
        if (config.apiKey.isBlank()) {
            return@withContext AIParseResult.Failure("Falta configurar la clave de ${config.displayName}")
        }

        val requestJson = json.encodeToString(
            ChatCompletionRequest.serializer(),
            ChatCompletionRequest(
                model = config.model,
                messages = listOf(
                    ChatMessage(role = "system", content = PromptBuilder.systemPrompt(timeProvider.today())),
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
    }
}
