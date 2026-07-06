package com.nehonar.operator.testing

import com.nehonar.operator.core.ai.AIParseResult
import com.nehonar.operator.core.ai.AIProvider
import com.nehonar.operator.core.ai.AIProviderType
import com.nehonar.operator.core.ai.IntentType
import com.nehonar.operator.core.ai.ParsedIntent
import com.nehonar.operator.core.ai.PriorMessage
import com.nehonar.operator.core.calendar.CalendarEvent
import com.nehonar.operator.core.calendar.CalendarRepository
import com.nehonar.operator.core.common.TimeProvider
import com.nehonar.operator.core.datastore.VoiceModePreference
import com.nehonar.operator.core.datastore.WakeWordSettings
import com.nehonar.operator.core.domain.model.ChecklistItem
import com.nehonar.operator.core.domain.model.MemoryFact
import com.nehonar.operator.core.domain.model.PlaceReminder
import com.nehonar.operator.core.domain.model.Reminder
import com.nehonar.operator.core.domain.model.ReminderStatus
import com.nehonar.operator.core.domain.model.SavedPlace
import com.nehonar.operator.core.domain.model.VoiceNote
import com.nehonar.operator.core.domain.repository.ChecklistRepository
import com.nehonar.operator.core.domain.repository.MemoryRepository
import com.nehonar.operator.core.domain.repository.ParsedIntentRepository
import com.nehonar.operator.core.domain.repository.PlaceReminderRepository
import com.nehonar.operator.core.domain.repository.PlaceRepository
import com.nehonar.operator.core.domain.repository.ReminderRepository
import com.nehonar.operator.core.domain.repository.VoiceNoteRepository
import com.nehonar.operator.core.location.GeofenceScheduler
import com.nehonar.operator.core.location.LatLng
import com.nehonar.operator.core.location.LocationProvider
import com.nehonar.operator.core.notifications.ReminderScheduler
import com.nehonar.operator.core.security.ApiKeyStore
import com.nehonar.operator.core.voice.Speaker
import com.nehonar.operator.core.voice.SpeechToText
import com.nehonar.operator.core.voice.SttEvent
import com.nehonar.operator.core.widget.WidgetRefresher
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

    override fun observeAll(): Flow<Map<String, ParsedIntent>> = intents
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
    var lastHistory: List<PriorMessage> = emptyList()
        private set

    override suspend fun parseVoiceNote(transcript: String, history: List<PriorMessage>): AIParseResult {
        lastTranscript = transcript
        lastHistory = history
        return result(transcript)
    }
}

class FakeReminderRepository : ReminderRepository {

    private val reminders = MutableStateFlow<Map<String, Reminder>>(emptyMap())

    val current: Map<String, Reminder> get() = reminders.value

    override fun observePending(): Flow<List<Reminder>> = reminders.map { map ->
        map.values.filter { it.status == ReminderStatus.PENDING }.sortedBy { it.triggerAt }
    }

    override suspend fun getById(id: String): Reminder? = reminders.value[id]

    override suspend fun getAllPending(): List<Reminder> =
        reminders.value.values.filter { it.status == ReminderStatus.PENDING }

    override suspend fun getAllForVoiceNote(voiceNoteId: String): List<Reminder> =
        reminders.value.values.filter { it.voiceNoteId == voiceNoteId }

    override suspend fun save(reminder: Reminder) {
        reminders.update { it + (reminder.id to reminder) }
    }

    override suspend fun delete(id: String) {
        reminders.update { it - id }
    }
}

class FakeReminderScheduler : ReminderScheduler {

    val scheduled = mutableListOf<Reminder>()
    val cancelled = mutableListOf<String>()
    var exactAlarmsEnabled: Boolean = true

    override fun schedule(reminder: Reminder) {
        scheduled += reminder
    }

    override fun cancel(reminderId: String) {
        cancelled += reminderId
    }

    override fun canScheduleExact(): Boolean = exactAlarmsEnabled
}

class FakeChecklistRepository : ChecklistRepository {

    private val items = MutableStateFlow<Map<String, ChecklistItem>>(emptyMap())

    val current: Map<String, ChecklistItem> get() = items.value

    override fun observeAll(): Flow<List<ChecklistItem>> = items.map { map ->
        map.values.sortedWith(compareBy({ it.done }, { it.createdAt }))
    }

    override suspend fun getById(id: String): ChecklistItem? = items.value[id]

    override suspend fun save(item: ChecklistItem) {
        items.update { it + (item.id to item) }
    }

    override suspend fun delete(id: String) {
        items.update { it - id }
    }

    override suspend fun deleteDone() {
        items.update { map -> map.filterValues { !it.done } }
    }

    override suspend fun deleteForVoiceNote(voiceNoteId: String) {
        items.update { map -> map.filterValues { it.voiceNoteId != voiceNoteId } }
    }
}

class FakeMemoryRepository : MemoryRepository {

    private val facts = MutableStateFlow<Map<String, MemoryFact>>(emptyMap())

    val current: Map<String, MemoryFact> get() = facts.value

    override fun observeAll(): Flow<List<MemoryFact>> =
        facts.map { map -> map.values.sortedByDescending { it.createdAt } }

    override suspend fun getRecent(limit: Int): List<MemoryFact> =
        facts.value.values.sortedByDescending { it.createdAt }.take(limit)

    override suspend fun save(fact: MemoryFact) {
        facts.update { it + (fact.id to fact) }
    }

    override suspend fun delete(id: String) {
        facts.update { it - id }
    }
}

class FakePlaceRepository : PlaceRepository {

    private val places = MutableStateFlow<Map<String, SavedPlace>>(emptyMap())

    val current: Map<String, SavedPlace> get() = places.value

    override fun observeAll(): Flow<List<SavedPlace>> =
        places.map { map -> map.values.sortedByDescending { it.createdAt } }

    override suspend fun getAll(): List<SavedPlace> = places.value.values.toList()

    override suspend fun getById(id: String): SavedPlace? = places.value[id]

    override suspend fun save(place: SavedPlace) {
        places.update { it + (place.id to place) }
    }

    override suspend fun delete(id: String) {
        places.update { it - id }
    }
}

class FakePlaceReminderRepository : PlaceReminderRepository {

    private val reminders = MutableStateFlow<Map<String, PlaceReminder>>(emptyMap())

    val current: Map<String, PlaceReminder> get() = reminders.value

    override fun observePending(): Flow<List<PlaceReminder>> = reminders.map { map ->
        map.values.filter { it.status == ReminderStatus.PENDING }
    }

    override suspend fun getAllPending(): List<PlaceReminder> =
        reminders.value.values.filter { it.status == ReminderStatus.PENDING }

    override suspend fun getById(id: String): PlaceReminder? = reminders.value[id]

    override suspend fun getAllForPlace(placeId: String): List<PlaceReminder> =
        reminders.value.values.filter { it.placeId == placeId }

    override suspend fun save(reminder: PlaceReminder) {
        reminders.update { it + (reminder.id to reminder) }
    }

    override suspend fun delete(id: String) {
        reminders.update { it - id }
    }
}

class FakeGeofenceScheduler(
    var backgroundGranted: Boolean = true,
) : GeofenceScheduler {

    val registered = mutableListOf<SavedPlace>()
    val unregistered = mutableListOf<String>()

    override fun canRegisterGeofences(): Boolean = backgroundGranted

    override fun register(place: SavedPlace) {
        registered += place
    }

    override fun unregister(placeId: String) {
        unregistered += placeId
    }
}

class FakeLocationProvider(
    var permission: Boolean = true,
    var location: LatLng? = LatLng(40.4168, -3.7038),
) : LocationProvider {
    override fun hasLocationPermission(): Boolean = permission
    override suspend fun currentLocation(): LatLng? = if (permission) location else null
}

class FakeCalendarRepository(
    var permission: Boolean = false,
    var events: List<CalendarEvent> = emptyList(),
) : CalendarRepository {

    override fun hasPermission(): Boolean = permission

    override suspend fun getEventsForToday(): List<CalendarEvent> =
        if (permission) events.sortedBy { it.startAt } else emptyList()

    override suspend fun getEventsForDays(days: Int): List<CalendarEvent> =
        if (permission) events.sortedBy { it.startAt } else emptyList()
}

class FakeWidgetRefresher : WidgetRefresher {

    var refreshCount = 0
        private set

    override fun refresh() {
        refreshCount++
    }
}

class FakeSpeaker : Speaker {

    val spoken = mutableListOf<String>()
    var stopCount = 0
        private set

    override val isSpeaking = MutableStateFlow(false)

    override fun speak(text: String) {
        spoken += text
    }

    override fun stop() {
        stopCount++
        isSpeaking.value = false
    }
}

class FakeVoiceModePreference(initial: Boolean = true) : VoiceModePreference {

    private val state = MutableStateFlow(initial)
    override val enabled: Flow<Boolean> = state

    override suspend fun setEnabled(enabled: Boolean) {
        state.value = enabled
    }
}

class FakeWakeWordSettings(
    enabledInitial: Boolean = false,
    phraseInitial: String = "operador",
    endPhraseInitial: String = "descansa",
) : WakeWordSettings {

    private val enabledState = MutableStateFlow(enabledInitial)
    private val phraseState = MutableStateFlow(phraseInitial)
    private val endPhraseState = MutableStateFlow(endPhraseInitial)
    override val enabled: Flow<Boolean> = enabledState
    override val phrase: Flow<String> = phraseState
    override val endPhrase: Flow<String> = endPhraseState

    override suspend fun setEnabled(enabled: Boolean) {
        enabledState.value = enabled
    }

    override suspend fun setPhrase(phrase: String) {
        phraseState.value = phrase
    }

    override suspend fun setEndPhrase(phrase: String) {
        endPhraseState.value = phrase
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
