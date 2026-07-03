package com.nehonar.operator.core.ai

import com.nehonar.operator.core.ai.remote.RemoteAIProvider
import com.nehonar.operator.core.ai.remote.RemoteAIProviderConfig
import com.nehonar.operator.core.common.TimeProvider
import com.nehonar.operator.core.datastore.OperatorPreferences
import com.nehonar.operator.core.security.ApiKeyStore
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient

/**
 * Único [AIProvider] inyectado por Hilt. Despacha a [MockAIProvider] o a
 * [RemoteAIProvider] según la preferencia actual, leída en cada llamada: así se puede
 * cambiar de proveedor desde Ajustes sin reconstruir el grafo de dependencias.
 */
class ConfigurableAIProvider @Inject constructor(
    private val mockAIProvider: MockAIProvider,
    private val preferences: OperatorPreferences,
    private val apiKeyStore: ApiKeyStore,
    private val httpClient: OkHttpClient,
    private val timeProvider: TimeProvider,
) : AIProvider {

    override suspend fun parseVoiceNote(transcript: String): AIParseResult {
        val providerType = preferences.aiProviderType.first()
        if (providerType == AIProviderType.MOCK) {
            return mockAIProvider.parseVoiceNote(transcript)
        }

        val baseUrl = providerType.defaultBaseUrl
            ?: return AIParseResult.Failure("Proveedor no soportado")
        val apiKey = apiKeyStore.get(providerType)
        if (apiKey.isNullOrBlank()) {
            return AIParseResult.Failure("Falta configurar la clave de ${providerType.displayName}")
        }
        val model = preferences.aiModel(providerType).first()

        val config = RemoteAIProviderConfig(
            displayName = providerType.displayName,
            baseUrl = baseUrl,
            model = model.ifBlank { providerType.defaultModel.orEmpty() },
            apiKey = apiKey,
        )
        return RemoteAIProvider(config, httpClient, timeProvider).parseVoiceNote(transcript)
    }
}
