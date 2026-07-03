package com.nehonar.operator.feature.console

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nehonar.operator.core.common.TimeProvider
import com.nehonar.operator.core.domain.model.ChecklistItem
import com.nehonar.operator.core.domain.model.Reminder
import com.nehonar.operator.core.domain.model.VoiceNote
import com.nehonar.operator.core.domain.repository.ChecklistRepository
import com.nehonar.operator.core.domain.repository.ReminderRepository
import com.nehonar.operator.core.domain.repository.VoiceNoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

enum class NodeKind { REMINDER, ACTION, NOTE }

data class ConsoleNode(
    val id: String,
    val kind: NodeKind,
)

data class ConsoleUiState(
    val nodes: List<ConsoleNode> = emptyList(),
    val pendingReminders: Int = 0,
    val openActions: Int = 0,
    val notesToday: Int = 0,
    /** 0..1: modula la cadencia del pulso y la velocidad orbital. */
    val activityLevel: Float = 0f,
) {
    val isActive: Boolean get() = nodes.isNotEmpty()
}

@HiltViewModel
class ConsoleViewModel @Inject constructor(
    private val timeProvider: TimeProvider,
    voiceNoteRepository: VoiceNoteRepository,
    reminderRepository: ReminderRepository,
    checklistRepository: ChecklistRepository,
) : ViewModel() {

    val uiState: StateFlow<ConsoleUiState> = combine(
        voiceNoteRepository.observeAll(),
        reminderRepository.observePending(),
        checklistRepository.observeAll(),
    ) { notes, reminders, checklist ->
        buildState(notes, reminders, checklist)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ConsoleUiState(),
    )

    private fun buildState(
        notes: List<VoiceNote>,
        reminders: List<Reminder>,
        checklist: List<ChecklistItem>,
    ): ConsoleUiState {
        val zone = ZoneId.systemDefault()
        val today = timeProvider.today()
        val notesToday = notes.filter { it.createdAt.atZone(zone).toLocalDate() == today }
        val openActions = checklist.filter { !it.done }

        val nodes = buildList {
            reminders.take(MAX_REMINDER_NODES).forEach { add(ConsoleNode(it.id, NodeKind.REMINDER)) }
            openActions.take(MAX_ACTION_NODES).forEach { add(ConsoleNode(it.id, NodeKind.ACTION)) }
            notesToday.take(MAX_NOTE_NODES).forEach { add(ConsoleNode(it.id, NodeKind.NOTE)) }
        }
        val totalItems = reminders.size + openActions.size + notesToday.size
        return ConsoleUiState(
            nodes = nodes,
            pendingReminders = reminders.size,
            openActions = openActions.size,
            notesToday = notesToday.size,
            activityLevel = (totalItems / ACTIVITY_SATURATION).coerceIn(0f, 1f),
        )
    }

    companion object {
        const val MAX_REMINDER_NODES = 6
        const val MAX_ACTION_NODES = 8
        const val MAX_NOTE_NODES = 6
        private const val ACTIVITY_SATURATION = 10f
    }
}
