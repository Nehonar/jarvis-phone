package com.nehonar.operator.testing

import com.nehonar.operator.core.ai.AIParseResult
import com.nehonar.operator.core.ai.AIProvider
import com.nehonar.operator.core.ai.AIProviderType
import com.nehonar.operator.core.ai.IntentType
import com.nehonar.operator.core.ai.ParsedIntent
import com.nehonar.operator.core.common.TimeProvider
import com.nehonar.operator.core.domain.model.VoiceNote
import com.nehonar.operator.core.domain.repository.ParsedIntentRepository
import com.nehonar.operator.core.domain.repository.VoiceNoteRepository
import com.nehonar.operator.core.security.ApiKeyStore
import com.nehonar.operator.core.voice.SpeechToText
import com.nehonar.operator.core.voice.SttEvent
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class FakeSpeechToText(
    private val events: List<SttEvent>,
    private val available: Boolean = true,
) : SpeechToText {
    override fun isAvailable(): Boolean = available
    override fun listen(): Flow<SttEvent> = events.asFlow()
}

/**
 * Cada llamada a [listen] consume la siguiente lista de [responses] en orden: útil
 * para simular varias rondas de una conversación (bucle de aclaración). Si se llama
 * más veces de las que hay respuestas, repite la última.
 *
 * Clase separada de [FakeSpeechToText] en vez de un constructor sobrecargado: en la
 * JVM, `List<List<SttEvent>>` y `List<SttEvent>` se borran al mismo tipo genérico y
 * dos constructores así colisionan ("platform declaration clash").
 */
class FakeSequentialSpeechToText(
    private val responses: List<List<SttEvent>>,
    private val available: Boolean = true,
) : SpeechToText {

    private var callIndex = 0

    override fun isAvailable(): Boolean = available

    override fun listen(): Flow<SttEvent> {
        val events = responses.getOrElse(callIndex) { responses.last() }
        callIndex++
        return events.asFlow()
    }
}

class FakeVoiceNoteRepository : VoiceNoteRepository {

    private val notes = MutableStateFlow<List<VoiceNote>>(emptyList())

    val current: List<VoiceNote> get() = notes.value

    override fun observeAll(): Flow<List<VoiceNote>> =
        notes.map { list -> list.sortedByDescending { it.createdAt } }

    override suspend fun getById(id: String): VoiceNote? = notes.value.firstOrNull { it.id == id }

    override suspend fun save(note: VoiceNote) {
        notes.update { list -> list.filterNot { it.id == note.id } + note }
    }

    override suspend fun delete(id: String) {
        notes.update { list -> list.filterNot { it.id == id } }
    }
}

class FakeParsedIntentRepository : ParsedIntentRepository {

    private val intents = MutableStateFlow<Map<String, ParsedIntent>>(emptyMap())

    val current: Map<String, ParsedIntent> get() = intents.value

    override fun observeByVoiceNoteId(voiceNoteId: String): Flow<ParsedIntent?> =
        intents.map { it[voiceNoteId] }

    override suspend fun getByVoiceNoteId(voiceNoteId: String): ParsedIntent? = intents.value[voiceNoteId]

    override suspend fun save(voiceNoteId: String, intent: ParsedIntent) {
        intents.update { it + (voiceNoteId to intent) }
    }

    override suspend fun deleteByVoiceNoteId(voiceNoteId: String) {
        intents.update { it - voiceNoteId }
    }

    override fun observeIntentTypesByVoiceNoteId(): Flow<Map<String, IntentType>> =
        intents.map { map -> map.mapValues { (_, intent) -> intent.intentType } }
}

class FakeAIProvider(
    private val result: (String) -> AIParseResult = { transcript ->
        AIParseResult.Success(
            ParsedIntent(
                intentType = IntentType.GENERAL_NOTE,
                confidence = 0.5f,
                title = "GENERAL NOTE",
                summary = transcript,
                assistantResponse = "Nota guardada.",
            ),
        )
    },
) : AIProvider {
    var lastTranscript: String? = null
        private set

    override suspend fun parseVoiceNote(transcript: String): AIParseResult {
        lastTranscript = transcript
        return result(transcript)
    }
}

class FakeApiKeyStore : ApiKeyStore {

    private val keys = mutableMapOf<AIProviderType, String>()

    override suspend fun get(provider: AIProviderType): String? = keys[provider]

    override suspend fun set(provider: AIProviderType, apiKey: String) {
        keys[provider] = apiKey
    }

    override suspend fun clear(provider: AIProviderType) {
        keys.remove(provider)
    }
}

class FixedTimeProvider(
    private val instant: Instant = Instant.parse("2026-07-02T10:15:00Z"),
) : TimeProvider {
    override fun now(): Instant = instant
    override fun today(): LocalDate = instant.atZone(ZoneOffset.UTC).toLocalDate()
}
