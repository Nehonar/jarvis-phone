package com.nehonar.operator.feature.review

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nehonar.operator.core.ai.AIParseResult
import com.nehonar.operator.core.ai.AIProvider
import com.nehonar.operator.core.ai.ParsedIntent
import com.nehonar.operator.core.common.TimeProvider
import com.nehonar.operator.core.domain.model.ChecklistItem
import com.nehonar.operator.core.domain.model.MemoryFact
import com.nehonar.operator.core.domain.model.Reminder
import com.nehonar.operator.core.domain.model.ReminderStatus
import com.nehonar.operator.core.domain.model.VoiceNoteStatus
import com.nehonar.operator.core.domain.repository.ChecklistRepository
import com.nehonar.operator.core.domain.repository.MemoryRepository
import com.nehonar.operator.core.domain.repository.ParsedIntentRepository
import com.nehonar.operator.core.domain.repository.ReminderRepository
import com.nehonar.operator.core.domain.repository.VoiceNoteRepository
import com.nehonar.operator.core.notifications.ReminderScheduler
import com.nehonar.operator.core.widget.WidgetRefresher
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeParseException
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ReviewUiState {
    data object Loading : ReviewUiState
    data class Content(
        val transcript: String,
        val intent: ParsedIntent,
        val isEditing: Boolean = false,
        val editError: String? = null,
    ) : ReviewUiState
    data object Done : ReviewUiState
    data object NotFound : ReviewUiState
}

@HiltViewModel
class ReviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val voiceNoteRepository: VoiceNoteRepository,
    private val parsedIntentRepository: ParsedIntentRepository,
    private val aiProvider: AIProvider,
    private val reminderRepository: ReminderRepository,
    private val reminderScheduler: ReminderScheduler,
    private val timeProvider: TimeProvider,
    private val widgetRefresher: WidgetRefresher,
    private val checklistRepository: ChecklistRepository,
    private val memoryRepository: MemoryRepository,
) : ViewModel() {

    // Navigation type-safe expone cada campo de ReviewRoute como argumento plano
    // bajo su nombre de propiedad; leerlo así evita depender del decodificador
    // de rutas serializadas dentro del ViewModel y es más simple de testear.
    private val voiceNoteId: String = checkNotNull(savedStateHandle["voiceNoteId"])

    private val _uiState = MutableStateFlow<ReviewUiState>(ReviewUiState.Loading)
    val uiState: StateFlow<ReviewUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val note = voiceNoteRepository.getById(voiceNoteId)
        val intent = parsedIntentRepository.getByVoiceNoteId(voiceNoteId)
        _uiState.value = if (note != null && intent != null) {
            ReviewUiState.Content(transcript = note.transcript, intent = intent)
        } else {
            ReviewUiState.NotFound
        }
    }

    fun startEditing() {
        val current = _uiState.value
        if (current is ReviewUiState.Content) {
            _uiState.value = current.copy(isEditing = true, editError = null)
        }
    }

    fun cancelEditing() {
        val current = _uiState.value
        if (current is ReviewUiState.Content) {
            _uiState.value = current.copy(isEditing = false)
        }
    }

    fun saveEditedText(newTranscript: String) {
        val current = _uiState.value
        if (current !is ReviewUiState.Content) return
        val transcript = newTranscript.trim()
        if (transcript.isEmpty()) return
        viewModelScope.launch {
            val note = voiceNoteRepository.getById(voiceNoteId) ?: return@launch
            when (val result = aiProvider.parseVoiceNote(transcript)) {
                is AIParseResult.Success -> {
                    voiceNoteRepository.save(
                        note.copy(transcript = transcript, status = VoiceNoteStatus.PARSED),
                    )
                    parsedIntentRepository.save(voiceNoteId, result.intent)
                    _uiState.value = ReviewUiState.Content(transcript = transcript, intent = result.intent)
                }
                is AIParseResult.Failure -> {
                    _uiState.value = current.copy(isEditing = true, editError = result.reason)
                }
            }
        }
    }

    fun accept() {
        val current = _uiState.value
        if (current !is ReviewUiState.Content) return
        viewModelScope.launch {
            parsedIntentRepository.save(voiceNoteId, current.intent.copy(needsConfirmation = false))
            scheduleReminderIfResolved(current.intent)
            saveChecklistItems(current.intent)
            saveMemoryFacts(current.intent)
            _uiState.value = ReviewUiState.Done
        }
    }

    private suspend fun saveMemoryFacts(intent: ParsedIntent) {
        intent.memoryFacts.forEach { draft ->
            memoryRepository.save(
                MemoryFact(
                    id = UUID.randomUUID().toString(),
                    topic = draft.topic,
                    fact = draft.fact,
                    createdAt = timeProvider.now(),
                ),
            )
        }
    }

    private suspend fun saveChecklistItems(intent: ParsedIntent) {
        intent.actions.forEach { action ->
            checklistRepository.save(
                ChecklistItem(
                    id = UUID.randomUUID().toString(),
                    voiceNoteId = voiceNoteId,
                    type = action.type,
                    label = action.label,
                    done = false,
                    createdAt = timeProvider.now(),
                ),
            )
        }
    }

    private suspend fun scheduleReminderIfResolved(intent: ParsedIntent) {
        val triggerAt = resolveTriggerInstant(intent) ?: return
        if (!triggerAt.isAfter(timeProvider.now())) return
        val message = intent.reminders.firstOrNull()?.message ?: intent.assistantResponse
        val reminder = Reminder(
            id = UUID.randomUUID().toString(),
            voiceNoteId = voiceNoteId,
            message = message,
            triggerAt = triggerAt,
            status = ReminderStatus.PENDING,
        )
        reminderRepository.save(reminder)
        reminderScheduler.schedule(reminder)
        widgetRefresher.refresh()
    }

    private fun resolveTriggerInstant(intent: ParsedIntent): Instant? {
        val date = intent.date ?: return null
        val time = intent.time ?: return null
        return try {
            LocalDateTime.of(LocalDate.parse(date), LocalTime.parse(time))
                .atZone(ZoneId.systemDefault())
                .toInstant()
        } catch (e: DateTimeParseException) {
            null
        }
    }

    fun discard() {
        viewModelScope.launch {
            // Cascada: si la nota ya se aceptó antes, puede tener recordatorio con
            // alarma viva e items de checklist; descartar limpia todo.
            reminderRepository.getAllForVoiceNote(voiceNoteId).forEach { reminder ->
                reminderScheduler.cancel(reminder.id)
                reminderRepository.delete(reminder.id)
            }
            checklistRepository.deleteForVoiceNote(voiceNoteId)
            parsedIntentRepository.deleteByVoiceNoteId(voiceNoteId)
            voiceNoteRepository.delete(voiceNoteId)
            widgetRefresher.refresh()
            _uiState.value = ReviewUiState.Done
        }
    }
}
