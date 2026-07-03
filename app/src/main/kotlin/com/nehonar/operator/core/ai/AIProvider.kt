package com.nehonar.operator.core.ai

sealed interface AIParseResult {
    data class Success(val intent: ParsedIntent) : AIParseResult
    data class Failure(val reason: String) : AIParseResult
}

interface AIProvider {
    suspend fun parseVoiceNote(transcript: String): AIParseResult
}
