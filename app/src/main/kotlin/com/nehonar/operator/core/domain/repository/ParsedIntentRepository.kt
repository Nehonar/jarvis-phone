package com.nehonar.operator.core.domain.repository

import com.nehonar.operator.core.ai.IntentType
import com.nehonar.operator.core.ai.ParsedIntent
import kotlinx.coroutines.flow.Flow

interface ParsedIntentRepository {
    fun observeByVoiceNoteId(voiceNoteId: String): Flow<ParsedIntent?>
    suspend fun getByVoiceNoteId(voiceNoteId: String): ParsedIntent?
    suspend fun save(voiceNoteId: String, intent: ParsedIntent)
    suspend fun deleteByVoiceNoteId(voiceNoteId: String)

    /** Para listados (historial): tipo de intención por nota, sin cargar el resto del contrato. */
    fun observeIntentTypesByVoiceNoteId(): Flow<Map<String, IntentType>>

    /** Todas las intenciones, por id de nota. Para agregados como el DayContext de Home. */
    fun observeAll(): Flow<Map<String, ParsedIntent>>
}
