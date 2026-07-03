package com.nehonar.operator.core.ai.remote

data class RemoteAIProviderConfig(
    val displayName: String,
    val baseUrl: String,
    val model: String,
    val apiKey: String,
)
