package com.nehonar.operator.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nehonar.operator.core.ai.AIParseResult
import com.nehonar.operator.core.ai.AIProvider
import com.nehonar.operator.core.ai.IntentType
import com.nehonar.operator.core.common.formatOperatorDateTime
import com.nehonar.operator.core.domain.model.VoiceNoteStatus
import com.nehonar.operator.core.domain.repository.ChecklistRepository
import com.nehonar.operator.core.domain.repository.ParsedIntentRepository
import com.nehonar.operator.core.domain.repository.ReminderRepository
import com.nehonar.operator.core.domain.repository.VoiceNoteRepository
import com.nehonar.operator.core.notifications.ReminderScheduler
import com.nehonar.operator.core.widget.WidgetRefresher
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HistoryItem(
    val id: String,
    val header: String,
    val status: VoiceNoteStatus,
    val transcript: String,
    val intentType: IntentType?,
) {
    val canRetry: Boolean get() = status == VoiceNoteStatus.TRANSCRIBED
}

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val voiceNoteRepository: VoiceNoteRepository,
    private val parsedIntentRepository: ParsedIntentRepository,
    private val aiProvider: AIProvider,
    private val reminderRepository: ReminderRepository,
    private val reminderScheduler: ReminderScheduler,
    private val widgetRefresher: WidgetRefresher,
    private val checklistRepository: ChecklistRepository,
) : ViewModel() {

    val items: StateFlow<List<HistoryItem>> = combine(
        voiceNoteRepository.observeAll(),
        parsedIntentRepository.observeIntentTypesByVoiceNoteId(),
    ) { notes, intentTypes ->
        notes.map { note ->
            HistoryItem(
                id = note.id,
                header = formatOperatorDateTime(note.createdAt, ZoneId.systemDefault()),
                status = note.status,
                transcript = note.transcript,
                intentType = intentTypes[note.id],
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    private val _navigateToReviewId = MutableStateFlow<String?>(null)
    val navigateToReviewId: StateFlow<String?> = _navigateToReviewId.asStateFlow()

    fun delete(id: String) {
        viewModelScope.launch {
            // Cascada: sin esto quedaban recordatorios huérfanos con la alarma viva
            // (y el widget seguía mostrándolos).
            reminderRepository.getAllForVoiceNote(id).forEach { reminder ->
                reminderScheduler.cancel(reminder.id)
                reminderRepository.delete(reminder.id)
            }
            checklistRepository.deleteForVoiceNote(id)
            parsedIntentRepository.deleteByVoiceNoteId(id)
            voiceNoteRepository.delete(id)
            widgetRefresher.refresh()
        }
    }

    fun retryParsing(voiceNoteId: String) {
        viewModelScope.launch {
            val note = voiceNoteRepository.getById(voiceNoteId) ?: return@launch
            when (val result = aiProvider.parseVoiceNote(note.transcript)) {
                is AIParseResult.Success -> {
                    parsedIntentRepository.save(voiceNoteId, result.intent)
                    voiceNoteRepository.save(note.copy(status = VoiceNoteStatus.PARSED))
                    _navigateToReviewId.value = voiceNoteId
                }
                is AIParseResult.Failure -> Unit // se queda pendiente, se puede reintentar de nuevo
            }
        }
    }

    fun consumeNavigation() {
        _navigateToReviewId.value = null
    }
}
