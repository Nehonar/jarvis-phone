package com.nehonar.operator.feature.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nehonar.operator.core.ai.AIParseResult
import com.nehonar.operator.core.ai.AIProvider
import com.nehonar.operator.core.common.TimeProvider
import com.nehonar.operator.core.domain.model.VoiceNote
import com.nehonar.operator.core.domain.model.VoiceNoteStatus
import com.nehonar.operator.core.domain.repository.ParsedIntentRepository
import com.nehonar.operator.core.domain.repository.VoiceNoteRepository
import com.nehonar.operator.core.voice.Speaker
import com.nehonar.operator.core.voice.SpeechToText
import com.nehonar.operator.core.voice.SttError
import com.nehonar.operator.core.voice.SttEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface CaptureUiState {
    data object Idle : CaptureUiState
    data class Listening(val partialText: String = "", val level: Float = 0f) : CaptureUiState
    data object Processing : CaptureUiState
    data class AwaitingAnswer(val prompt: String) : CaptureUiState
    data class Parsed(val voiceNoteId: String) : CaptureUiState
    data class SavedPending(val voiceNoteId: String, val reason: String) : CaptureUiState
    data class Error(val message: String, val canRetry: Boolean = true) : CaptureUiState
}

@HiltViewModel
class CaptureViewModel @Inject constructor(
    private val speechToText: SpeechToText,
    private val voiceNoteRepository: VoiceNoteRepository,
    private val parsedIntentRepository: ParsedIntentRepository,
    private val aiProvider: AIProvider,
    private val timeProvider: TimeProvider,
    private val speaker: Speaker,
) : ViewModel() {

    private val _uiState = MutableStateFlow<CaptureUiState>(CaptureUiState.Idle)
    val uiState: StateFlow<CaptureUiState> = _uiState.asStateFlow()

    private var captureJob: Job? = null

    // Estado de la conversación en curso (una nota puede tardar varias rondas de
    // preguntas en completarse). Se reinicia en cada startCapture() nuevo.
    private var noteId: String? = null
    private var pendingTranscript: String? = null
    private var roundsLeft: Int = MAX_CLARIFICATION_ROUNDS

    /** Empieza una nota nueva desde cero. */
    fun startCapture() {
        noteId = null
        pendingTranscript = null
        roundsLeft = MAX_CLARIFICATION_ROUNDS
        beginListening()
    }

    /** Continúa la misma nota: escucha la respuesta a la pregunta pendiente. */
    fun startAnsweringClarification() {
        if (_uiState.value !is CaptureUiState.AwaitingAnswer) return
        beginListening()
    }

    private fun beginListening() {
        if (captureJob?.isActive == true) return
        if (!speechToText.isAvailable()) {
            _uiState.value = CaptureUiState.Error(
                message = SttError.UNAVAILABLE.toOperatorMessage(),
                canRetry = false,
            )
            return
        }
        _uiState.value = CaptureUiState.Listening()
        captureJob = viewModelScope.launch {
            speechToText.listen().collect { event -> onSttEvent(event) }
        }
    }

    fun cancelCapture() {
        captureJob?.cancel()
        captureJob = null
        noteId = null
        pendingTranscript = null
        roundsLeft = MAX_CLARIFICATION_ROUNDS
        _uiState.value = CaptureUiState.Idle
    }

    fun onPermissionDenied() {
        _uiState.value = CaptureUiState.Error(
            message = "PERMISO DE MICRÓFONO DENEGADO // CONCEDER Y REINTENTAR",
            canRetry = true,
        )
    }

    /** Vuelve a Idle tras haber navegado a la revisión, para no re-disparar la navegación. */
    fun onNavigatedToReview() {
        if (_uiState.value is CaptureUiState.Parsed) {
            _uiState.value = CaptureUiState.Idle
        }
    }

    /** Vuelve a Idle tras haber navegado al historial desde un SavedPending. */
    fun onNavigatedToHistory() {
        if (_uiState.value is CaptureUiState.SavedPending) {
            _uiState.value = CaptureUiState.Idle
        }
    }

    private suspend fun onSttEvent(event: SttEvent) {
        when (event) {
            SttEvent.Ready, SttEvent.SpeechStart -> {
                if (_uiState.value !is CaptureUiState.Listening) {
                    _uiState.value = CaptureUiState.Listening()
                }
            }
            is SttEvent.Level -> {
                val current = _uiState.value
                if (current is CaptureUiState.Listening) {
                    _uiState.value = current.copy(level = event.rmsDb)
                }
            }
            is SttEvent.Partial -> {
                val level = (_uiState.value as? CaptureUiState.Listening)?.level ?: 0f
                _uiState.value = CaptureUiState.Listening(partialText = event.text, level = level)
            }
            SttEvent.SpeechEnd -> {
                _uiState.value = CaptureUiState.Processing
            }
            is SttEvent.FinalResult -> onFinalResult(event.text)
            is SttEvent.Failed -> {
                _uiState.value = CaptureUiState.Error(message = event.error.toOperatorMessage())
            }
        }
    }

    private suspend fun onFinalResult(text: String) {
        val heard = text.trim()
        if (heard.isEmpty()) {
            _uiState.value = CaptureUiState.Error(message = SttError.NO_MATCH.toOperatorMessage())
            return
        }
        _uiState.value = CaptureUiState.Processing
        val combined = pendingTranscript?.let { previous -> "$previous. $heard" } ?: heard
        processTranscript(combined)
    }

    /**
     * Guarda/actualiza la nota con la transcripción acumulada e interpreta.
     * Si falta un dato y quedan rondas, pide la respuesta en la misma sesión en vez
     * de cerrar la nota; si se agotan las rondas, sigue adelante con lo que haya
     * (Review ya muestra el aviso de "pregunta pendiente" en ese caso).
     */
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
                // El operador siempre da feedback hablado: la pregunta pendiente, la
                // confirmación ("anotado, señor") o la respuesta a una consulta. El
                // silencio ("modo texto") lo decide el propio Speaker según preferencia.
                speaker.speak(intent.assistantResponse)
                if (intent.clarifyingQuestions.isNotEmpty() && roundsLeft > 0) {
                    pendingTranscript = transcript
                    roundsLeft -= 1
                    _uiState.value = CaptureUiState.AwaitingAnswer(prompt = intent.assistantResponse)
                } else {
                    parsedIntentRepository.save(currentNoteId, intent)
                    voiceNoteRepository.save(note.copy(status = VoiceNoteStatus.PARSED))
                    pendingTranscript = null
                    _uiState.value = CaptureUiState.Parsed(currentNoteId)
                }
            }
            is AIParseResult.Failure -> {
                _uiState.value = CaptureUiState.SavedPending(currentNoteId, result.reason)
            }
        }
    }

    private companion object {
        const val MAX_CLARIFICATION_ROUNDS = 3
    }
}

fun SttError.toOperatorMessage(): String = when (this) {
    SttError.NO_MATCH -> "SIN COINCIDENCIA // REPITE LA NOTA"
    SttError.NO_SPEECH -> "SIN VOZ DETECTADA // REINTENTAR"
    SttError.NETWORK -> "ERROR DE RED // STT"
    SttError.PERMISSION -> "PERMISO DE MICRÓFONO DENEGADO"
    SttError.BUSY -> "RECONOCEDOR OCUPADO // REINTENTAR"
    SttError.UNAVAILABLE -> "STT NO DISPONIBLE EN ESTE DISPOSITIVO"
    SttError.OTHER -> "ERROR STT // REINTENTAR"
}
