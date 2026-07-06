package com.nehonar.operator.feature.conversation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nehonar.operator.core.ai.AIParseResult
import com.nehonar.operator.core.ai.AIProvider
import com.nehonar.operator.core.ai.IntentType
import com.nehonar.operator.core.ai.ParsedIntent
import com.nehonar.operator.core.ai.PriorMessage
import com.nehonar.operator.core.ai.ReminderTrigger
import com.nehonar.operator.core.common.TimeProvider
import com.nehonar.operator.core.datastore.VoiceModePreference
import com.nehonar.operator.core.datastore.WakeWordSettings
import com.nehonar.operator.core.domain.IntentCommitter
import com.nehonar.operator.core.domain.model.VoiceNote
import com.nehonar.operator.core.domain.model.VoiceNoteStatus
import com.nehonar.operator.core.domain.repository.ChecklistRepository
import com.nehonar.operator.core.domain.repository.ReminderRepository
import com.nehonar.operator.core.domain.repository.VoiceNoteRepository
import com.nehonar.operator.core.notifications.ReminderScheduler
import com.nehonar.operator.core.voice.Speaker
import com.nehonar.operator.core.voice.SpeechToText
import com.nehonar.operator.core.voice.SttError
import com.nehonar.operator.core.voice.SttEvent
import com.nehonar.operator.core.widget.WidgetRefresher
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class ConversationMode { SPEAK, SILENCE }

/** Escucha continua en primer plano: STANDBY vigila la frase; ACTIVE atiende un comando. */
enum class WakeState { STANDBY, ACTIVE }

enum class Author { USER, OPERATOR }

/** Tarjeta visual que resume lo que el operador acaba de hacer con una nota. */
sealed interface ResultCard {
    data class Reminder(val message: String, val whenLabel: String) : ResultCard
    data class PlaceReminder(val message: String, val place: String) : ResultCard
    data class Checklist(val labels: List<String>) : ResultCard
    data class Memory(val facts: List<String>) : ResultCard
    data class NearbySearch(val query: String) : ResultCard
}

data class ConversationTurn(
    val id: String,
    val author: Author,
    val text: String,
    val cards: List<ResultCard> = emptyList(),
)

sealed interface ConversationStatus {
    data object Idle : ConversationStatus
    data class Listening(val partial: String = "", val level: Float = 0f) : ConversationStatus
    data object Processing : ConversationStatus
    data class AwaitingAnswer(val prompt: String) : ConversationStatus
    data class AwaitingConfirmation(val prompt: String) : ConversationStatus
    data class Error(val message: String) : ConversationStatus
}

data class ConversationUiState(
    val turns: List<ConversationTurn> = emptyList(),
    val status: ConversationStatus = ConversationStatus.Idle,
    val mode: ConversationMode = ConversationMode.SPEAK,
    /** Estado de la escucha continua; null si está desactivada. */
    val wake: WakeState? = null,
)

/**
 * Núcleo conversacional: el usuario habla o escribe, el operador responde (voz en
 * modo hablar, texto en modo silencio), confirma cada acción y muestra tarjetas de
 * resultado. La orden se ejecuta directamente (sin pantalla de revisión aparte);
 * las órdenes de navegación por voz abren secciones sin menú visible.
 */
@HiltViewModel
class ConversationViewModel @Inject constructor(
    private val speechToText: SpeechToText,
    private val speaker: Speaker,
    private val aiProvider: AIProvider,
    private val voiceNoteRepository: VoiceNoteRepository,
    private val intentCommitter: IntentCommitter,
    private val timeProvider: TimeProvider,
    private val voiceMode: VoiceModePreference,
    private val reminderRepository: ReminderRepository,
    private val reminderScheduler: ReminderScheduler,
    private val checklistRepository: ChecklistRepository,
    private val widgetRefresher: WidgetRefresher,
    private val wakeWordSettings: WakeWordSettings,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConversationUiState())
    val uiState: StateFlow<ConversationUiState> = _uiState.asStateFlow()

    private val navigationChannel = Channel<OperatorDestination>(Channel.BUFFERED)
    val navigation: Flow<OperatorDestination> = navigationChannel.receiveAsFlow()

    private var listenJob: Job? = null
    private var handsFreeJob: Job? = null
    private var noteId: String? = null
    private var pendingTranscript: String? = null
    private var roundsLeft: Int = MAX_CLARIFICATION_ROUNDS
    private var pendingDelete: DeletableItem? = null
    private var wakeEnabled = false
    private var wakePhrase = ""

    init {
        viewModelScope.launch {
            voiceMode.enabled.collect { enabled ->
                _uiState.value = _uiState.value.copy(
                    mode = if (enabled) ConversationMode.SPEAK else ConversationMode.SILENCE,
                )
            }
        }
        viewModelScope.launch {
            combine(wakeWordSettings.enabled, wakeWordSettings.phrase) { on, phrase -> on to phrase }
                .collect { (on, phrase) ->
                    wakeEnabled = on
                    wakePhrase = phrase
                    if (!on) stopHandsFree()
                }
        }
    }

    /** Alterna entre modo hablar (voz) y modo silencio (texto). */
    fun toggleMute() {
        viewModelScope.launch {
            val enabled = _uiState.value.mode == ConversationMode.SPEAK
            voiceMode.setEnabled(!enabled)
            if (enabled) speaker.stop()
        }
    }

    fun startTalking() {
        if (listenJob?.isActive == true) return
        if (!speechToText.isAvailable()) {
            setStatus(ConversationStatus.Error(SttError.UNAVAILABLE.toMessage()))
            return
        }
        setStatus(ConversationStatus.Listening())
        listenJob = viewModelScope.launch {
            speechToText.listen().collect { event -> onSttEvent(event) }
        }
    }

    fun cancelListening() {
        listenJob?.cancel()
        listenJob = null
        setStatus(ConversationStatus.Idle)
    }

    /**
     * Escucha continua en primer plano (llamar al reanudar la pantalla con permiso
     * de micrófono). En STANDBY solo reacciona a la frase de activación; al oírla
     * pasa a ACTIVE, atiende un comando y vuelve a STANDBY. Device-only (STT en
     * bucle); el bucle no se testea, sí la máquina de estados (ver D-021).
     */
    fun startHandsFreeIfEnabled() {
        if (!wakeEnabled || handsFreeJob?.isActive == true) return
        if (!speechToText.isAvailable()) return
        beginHandsFreeSession(wakePhrase)
        handsFreeJob = viewModelScope.launch {
            while (isActive && wakeEnabled) {
                var heard: String? = null
                speechToText.listen().collect { event ->
                    if (event is SttEvent.FinalResult) heard = event.text
                }
                val text = heard?.trim()
                if (!text.isNullOrEmpty()) handleWakeUtterance(text)
                if (!wakeEnabled) break
                delay(HANDS_FREE_PAUSE_MS)
            }
            _uiState.value = _uiState.value.copy(wake = null)
        }
    }

    fun stopHandsFree() {
        handsFreeJob?.cancel()
        handsFreeJob = null
        _uiState.value = _uiState.value.copy(wake = null)
    }

    internal fun beginHandsFreeSession(phrase: String) {
        wakePhrase = phrase
        _uiState.value = _uiState.value.copy(wake = WakeState.STANDBY)
    }

    /** Máquina de estados de la escucha por frase de activación (testeable). */
    internal suspend fun handleWakeUtterance(text: String) {
        when (_uiState.value.wake) {
            WakeState.ACTIVE -> {
                handleUtterance(text)
                _uiState.value = _uiState.value.copy(wake = WakeState.STANDBY)
            }
            WakeState.STANDBY, null -> {
                val match = WakePhraseMatcher.match(text, wakePhrase) ?: return
                _uiState.value = _uiState.value.copy(wake = WakeState.ACTIVE)
                if (match.remainder.isNotBlank()) {
                    handleUtterance(match.remainder)
                    _uiState.value = _uiState.value.copy(wake = WakeState.STANDBY)
                } else {
                    val reply = "Le escucho, señor."
                    speaker.speak(reply)
                    addTurn(Author.OPERATOR, reply)
                }
            }
        }
    }

    fun onPermissionDenied() {
        setStatus(ConversationStatus.Error("PERMISO DE MICRÓFONO DENEGADO // CONCEDER Y REINTENTAR"))
    }

    /** Entrada escrita (modo silencio). */
    fun sendText(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch { handleUtterance(clean) }
    }

    private suspend fun onSttEvent(event: SttEvent) {
        when (event) {
            SttEvent.Ready, SttEvent.SpeechStart -> {
                if (_uiState.value.status !is ConversationStatus.Listening) {
                    setStatus(ConversationStatus.Listening())
                }
            }
            is SttEvent.Level -> {
                val current = _uiState.value.status
                if (current is ConversationStatus.Listening) {
                    setStatus(current.copy(level = event.rmsDb))
                }
            }
            is SttEvent.Partial -> {
                val level = (_uiState.value.status as? ConversationStatus.Listening)?.level ?: 0f
                setStatus(ConversationStatus.Listening(partial = event.text, level = level))
            }
            SttEvent.SpeechEnd -> setStatus(ConversationStatus.Processing)
            is SttEvent.FinalResult -> {
                val heard = event.text.trim()
                if (heard.isEmpty()) {
                    setStatus(ConversationStatus.Error(SttError.NO_MATCH.toMessage()))
                } else {
                    handleUtterance(heard)
                }
            }
            is SttEvent.Failed -> setStatus(ConversationStatus.Error(event.error.toMessage()))
        }
    }

    private suspend fun handleUtterance(text: String) {
        addTurn(Author.USER, text)

        // Si hay un borrado pendiente, este turno es la respuesta sí/no.
        val pending = pendingDelete
        if (pending != null) {
            pendingDelete = null
            when {
                isAffirmative(text) -> confirmDelete(pending)
                isNegative(text) -> cancelDelete()
                else -> handleCommand(text) // respuesta ambigua: no borro, lo trato como orden
            }
            return
        }
        handleCommand(text)
    }

    private suspend fun handleCommand(text: String) {
        // Solo se interpreta como navegación si no estamos en mitad de una
        // aclaración (donde el texto es la respuesta a completar la nota).
        if (pendingTranscript == null) {
            NavigationMatcher.match(text)?.let { destination ->
                val reply = "Enseguida, señor. Abriendo ${destination.spokenName}."
                addTurn(Author.OPERATOR, reply)
                speaker.speak(reply)
                resetConversation()
                setStatus(ConversationStatus.Idle)
                navigationChannel.send(destination)
                return
            }
        }

        setStatus(ConversationStatus.Processing)
        val combined = pendingTranscript?.let { "$it. $text" } ?: text
        processTranscript(combined)
    }

    private suspend fun processTranscript(transcript: String) {
        val currentNoteId = noteId ?: UUID.randomUUID().toString().also { noteId = it }
        val note = VoiceNote(
            id = currentNoteId,
            audioUri = null,
            transcript = transcript,
            createdAt = timeProvider.now(),
            status = VoiceNoteStatus.TRANSCRIBED,
        )
        voiceNoteRepository.save(note)

        when (val result = aiProvider.parseVoiceNote(transcript, recentHistory())) {
            is AIParseResult.Success -> {
                val intent = result.intent
                when {
                    intent.intentType == IntentType.DELETE -> handleDeleteIntent(intent)
                    intent.clarifyingQuestions.isNotEmpty() && roundsLeft > 0 -> {
                        speaker.speak(intent.assistantResponse)
                        pendingTranscript = transcript
                        roundsLeft -= 1
                        addTurn(Author.OPERATOR, intent.assistantResponse)
                        setStatus(ConversationStatus.AwaitingAnswer(intent.assistantResponse))
                    }
                    else -> {
                        speaker.speak(intent.assistantResponse)
                        intentCommitter.commit(currentNoteId, intent)
                        voiceNoteRepository.save(note.copy(status = VoiceNoteStatus.PARSED))
                        addTurn(Author.OPERATOR, intent.assistantResponse, cards = buildCards(intent))
                        resetConversation()
                        setStatus(ConversationStatus.Idle)
                    }
                }
            }
            is AIParseResult.Failure -> {
                val reply = "No he podido procesarlo ahora, señor: ${result.reason}."
                speaker.speak(reply)
                addTurn(Author.OPERATOR, reply)
                resetConversation()
                setStatus(ConversationStatus.Idle)
            }
        }
    }

    /** Turnos previos (sin el actual) como contexto para la IA: resolver "bórrala"… */
    private fun recentHistory(): List<PriorMessage> =
        _uiState.value.turns
            .dropLast(1)
            .takeLast(HISTORY_TURNS)
            .map { PriorMessage(fromUser = it.author == Author.USER, text = it.text) }

    /**
     * Petición de borrado: busca el elemento (por el texto que dio la IA o, si no,
     * por la última respuesta del operador) y pide confirmación antes de borrar.
     */
    private suspend fun handleDeleteIntent(intent: ParsedIntent) {
        val query = intent.deleteQuery?.takeIf { it.isNotBlank() } ?: lastOperatorText()
        val candidate = findDeleteCandidate(query)
        if (candidate == null) {
            val reply = "No encuentro nada que borrar con eso, señor. ¿Qué elimino?"
            speaker.speak(reply)
            addTurn(Author.OPERATOR, reply)
            resetConversation()
            setStatus(ConversationStatus.Idle)
            return
        }
        pendingDelete = candidate
        val reply = "¿Borro «${candidate.label}», señor?"
        speaker.speak(reply)
        addTurn(Author.OPERATOR, reply)
        resetConversation()
        setStatus(ConversationStatus.AwaitingConfirmation(reply))
    }

    private suspend fun confirmDelete(item: DeletableItem) {
        when (item) {
            is DeletableItem.ReminderItem -> {
                reminderScheduler.cancel(item.id)
                reminderRepository.delete(item.id)
            }
            is DeletableItem.ChecklistEntry -> checklistRepository.delete(item.id)
        }
        widgetRefresher.refresh()
        val reply = "Hecho, señor. He borrado «${item.label}»."
        speaker.speak(reply)
        addTurn(Author.OPERATOR, reply)
        setStatus(ConversationStatus.Idle)
    }

    private fun cancelDelete() {
        val reply = "Como quiera, señor. No he borrado nada."
        speaker.speak(reply)
        addTurn(Author.OPERATOR, reply)
        setStatus(ConversationStatus.Idle)
    }

    /** Mejor coincidencia entre recordatorios pendientes y checklist abierta. */
    private suspend fun findDeleteCandidate(query: String?): DeletableItem? {
        if (query.isNullOrBlank()) return null
        val items = buildList {
            reminderRepository.getAllPending().forEach { add(DeletableItem.ReminderItem(it.id, it.message)) }
            checklistRepository.observeAll().first().filter { !it.done }
                .forEach { add(DeletableItem.ChecklistEntry(it.id, it.label)) }
        }
        val q = normalize(query)
        return items
            .map { it to matchScore(q, normalize(it.label)) }
            .filter { it.second > 0f }
            .maxByOrNull { it.second }
            ?.first
    }

    private fun lastOperatorText(): String? =
        _uiState.value.turns.lastOrNull { it.author == Author.OPERATOR }?.text

    private fun buildCards(intent: ParsedIntent): List<ResultCard> = buildList {
        intent.reminders.forEach { reminder ->
            when {
                reminder.trigger == ReminderTrigger.NEAR_LOCATION && reminder.place != null ->
                    add(ResultCard.PlaceReminder(reminder.message, reminder.place))
                intent.date != null && intent.time != null ->
                    add(ResultCard.Reminder(reminder.message, "${intent.date} · ${intent.time}"))
            }
        }
        if (intent.actions.isNotEmpty()) {
            add(ResultCard.Checklist(intent.actions.map { it.label }))
        }
        if (intent.memoryFacts.isNotEmpty()) {
            add(ResultCard.Memory(intent.memoryFacts.map { it.fact }))
        }
        intent.mapQuery?.let { add(ResultCard.NearbySearch(it)) }
    }

    private fun addTurn(author: Author, text: String, cards: List<ResultCard> = emptyList()) {
        val turn = ConversationTurn(UUID.randomUUID().toString(), author, text, cards)
        _uiState.value = _uiState.value.copy(turns = _uiState.value.turns + turn)
    }

    private fun setStatus(status: ConversationStatus) {
        _uiState.value = _uiState.value.copy(status = status)
    }

    private fun resetConversation() {
        noteId = null
        pendingTranscript = null
        roundsLeft = MAX_CLARIFICATION_ROUNDS
    }

    private fun isAffirmative(text: String): Boolean = matchesWord(text, AFFIRMATIVE)

    private fun isNegative(text: String): Boolean = matchesWord(text, NEGATIVE)

    private fun matchesWord(text: String, words: List<String>): Boolean {
        val padded = " ${normalize(text)} "
        return words.any { padded.contains(" $it ") }
    }

    private fun normalize(text: String): String =
        text.lowercase()
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    /** 1.0 si una contiene a la otra; si no, solapamiento de palabras (0..1). */
    private fun matchScore(query: String, label: String): Float {
        if (query.isEmpty() || label.isEmpty()) return 0f
        if (label.contains(query) || query.contains(label)) return 1f
        val qWords = query.split(" ").filter { it.length > 2 }.toSet()
        val lWords = label.split(" ").filter { it.length > 2 }.toSet()
        if (qWords.isEmpty() || lWords.isEmpty()) return 0f
        val common = qWords.count { it in lWords }
        return common.toFloat() / minOf(qWords.size, lWords.size)
    }

    private sealed interface DeletableItem {
        val label: String
        data class ReminderItem(val id: String, override val label: String) : DeletableItem
        data class ChecklistEntry(val id: String, override val label: String) : DeletableItem
    }

    private fun SttError.toMessage(): String = when (this) {
        SttError.NO_MATCH -> "SIN COINCIDENCIA // REPITE"
        SttError.NO_SPEECH -> "SIN VOZ DETECTADA // REINTENTAR"
        SttError.NETWORK -> "ERROR DE RED // STT"
        SttError.PERMISSION -> "PERMISO DE MICRÓFONO DENEGADO"
        SttError.BUSY -> "RECONOCEDOR OCUPADO // REINTENTAR"
        SttError.UNAVAILABLE -> "STT NO DISPONIBLE EN ESTE DISPOSITIVO"
        SttError.OTHER -> "ERROR STT // REINTENTAR"
    }

    private companion object {
        const val MAX_CLARIFICATION_ROUNDS = 3
        const val HISTORY_TURNS = 6
        const val HANDS_FREE_PAUSE_MS = 400L
        val AFFIRMATIVE = listOf(
            "sí", "si", "claro", "confirmo", "adelante", "hazlo", "vale", "correcto",
            "exacto", "eso es", "dale", "ok", "okay", "borra", "bórrala", "borrala",
        )
        val NEGATIVE = listOf("no", "déjalo", "dejalo", "cancela", "nada", "para", "espera")
    }
}
