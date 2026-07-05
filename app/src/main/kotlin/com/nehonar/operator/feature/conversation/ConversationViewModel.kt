package com.nehonar.operator.feature.conversation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nehonar.operator.core.ai.AIParseResult
import com.nehonar.operator.core.ai.AIProvider
import com.nehonar.operator.core.ai.ParsedIntent
import com.nehonar.operator.core.ai.ReminderTrigger
import com.nehonar.operator.core.common.TimeProvider
import com.nehonar.operator.core.datastore.VoiceModePreference
import com.nehonar.operator.core.domain.IntentCommitter
import com.nehonar.operator.core.domain.model.VoiceNote
import com.nehonar.operator.core.domain.model.VoiceNoteStatus
import com.nehonar.operator.core.domain.repository.VoiceNoteRepository
import com.nehonar.operator.core.voice.Speaker
import com.nehonar.operator.core.voice.SpeechToText
import com.nehonar.operator.core.voice.SttError
import com.nehonar.operator.core.voice.SttEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

enum class ConversationMode { SPEAK, SILENCE }

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
    data class Error(val message: String) : ConversationStatus
}

data class ConversationUiState(
    val turns: List<ConversationTurn> = emptyList(),
    val status: ConversationStatus = ConversationStatus.Idle,
    val mode: ConversationMode = ConversationMode.SPEAK,
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
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConversationUiState())
    val uiState: StateFlow<ConversationUiState> = _uiState.asStateFlow()

    private val navigationChannel = Channel<OperatorDestination>(Channel.BUFFERED)
    val navigation: Flow<OperatorDestination> = navigationChannel.receiveAsFlow()

    private var listenJob: Job? = null
    private var noteId: String? = null
    private var pendingTranscript: String? = null
    private var roundsLeft: Int = MAX_CLARIFICATION_ROUNDS

    init {
        viewModelScope.launch {
            voiceMode.enabled.collect { enabled ->
                _uiState.value = _uiState.value.copy(
                    mode = if (enabled) ConversationMode.SPEAK else ConversationMode.SILENCE,
                )
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

        when (val result = aiProvider.parseVoiceNote(transcript)) {
            is AIParseResult.Success -> {
                val intent = result.intent
                speaker.speak(intent.assistantResponse)
                if (intent.clarifyingQuestions.isNotEmpty() && roundsLeft > 0) {
                    pendingTranscript = transcript
                    roundsLeft -= 1
                    addTurn(Author.OPERATOR, intent.assistantResponse)
                    setStatus(ConversationStatus.AwaitingAnswer(intent.assistantResponse))
                } else {
                    intentCommitter.commit(currentNoteId, intent)
                    voiceNoteRepository.save(note.copy(status = VoiceNoteStatus.PARSED))
                    addTurn(Author.OPERATOR, intent.assistantResponse, cards = buildCards(intent))
                    resetConversation()
                    setStatus(ConversationStatus.Idle)
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
    }
}
