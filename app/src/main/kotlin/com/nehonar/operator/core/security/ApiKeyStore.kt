package com.nehonar.operator.core.security

import com.nehonar.operator.core.ai.AIProviderType

/** Guarda API keys cifradas, una por proveedor. Nunca se registra la clave en claro. */
interface ApiKeyStore {
    suspend fun get(provider: AIProviderType): String?
    suspend fun set(provider: AIProviderType, apiKey: String)
    suspend fun clear(provider: AIProviderType)
}
