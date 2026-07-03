package com.nehonar.operator.core.ai

enum class AIProviderType(
    val displayName: String,
    val defaultBaseUrl: String?,
    val defaultModel: String?,
) {
    MOCK("MOCK", defaultBaseUrl = null, defaultModel = null),
    DEEPSEEK("DEEPSEEK", "https://api.deepseek.com/v1", "deepseek-chat"),
    OPENAI("OPENAI", "https://api.openai.com/v1", "gpt-4o-mini"),
    GEMINI("GEMINI", "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-2.5-flash"),
}
