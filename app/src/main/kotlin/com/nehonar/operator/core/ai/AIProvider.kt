package com.nehonar.operator.core.ai

interface AIProvider {
    suspend fun parseVoiceNote(transcript: String): ParsedIntent
}
