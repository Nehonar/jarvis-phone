package com.nehonar.operator.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nehonar.operator.core.ai.ParsedIntent
import com.nehonar.operator.core.calendar.CalendarEvent
import com.nehonar.operator.core.calendar.CalendarRepository
import com.nehonar.operator.core.common.TimeProvider
import com.nehonar.operator.core.common.formatOperatorDate
import com.nehonar.operator.core.common.formatOperatorDateTime
import com.nehonar.operator.core.common.formatOperatorTime
import com.nehonar.operator.core.domain.model.ChecklistItem
import com.nehonar.operator.core.domain.model.Reminder
import com.nehonar.operator.core.domain.model.VoiceNote
import com.nehonar.operator.core.domain.repository.ChecklistRepository
import com.nehonar.operator.core.domain.repository.ParsedIntentRepository
import com.nehonar.operator.core.domain.repository.ReminderRepository
import com.nehonar.operator.core.domain.repository.VoiceNoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DayReminder(
    val timeLabel: String,
    val message: String,
)

data class FeedItem(
    val time: String,
    val tag: String,
    val text: String,
)

data class HomeUiState(
    val dateLabel: String,
    val noteCount: Int = 0,
    val nextReminder: DayReminder? = null,
    val pendingReminders: Int = 0,
    val awaitingReview: Int = 0,
    val openActions: Int = 0,
    val feed: List<FeedItem> = emptyList(),
    val calendarConnected: Boolean = false,
    val nextEventLabel: String? = null,
    val eventsToday: Int = 0,
)

private data class AgendaSnapshot(
    val hasPermission: Boolean = false,
    val events: List<CalendarEvent> = emptyList(),
)

private const val FEED_MAX_ITEMS = 4

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val timeProvider: TimeProvider,
    voiceNoteRepository: VoiceNoteRepository,
    parsedIntentRepository: ParsedIntentRepository,
    reminderRepository: ReminderRepository,
    checklistRepository: ChecklistRepository,
    private val calendarRepository: CalendarRepository,
) : ViewModel() {

    private val dateLabel = formatOperatorDate(timeProvider.today())

    private val agenda = MutableStateFlow(AgendaSnapshot())

    init {
        refreshAgenda()
    }

    /** ON_RESUME y tras conceder el permiso: los eventos cambian fuera de la app. */
    fun refreshAgenda() {
        viewModelScope.launch {
            agenda.value = AgendaSnapshot(
                hasPermission = calendarRepository.hasPermission(),
                events = calendarRepository.getEventsForToday(),
            )
        }
    }

    val uiState: StateFlow<HomeUiState> = combine(
        voiceNoteRepository.observeAll(),
        parsedIntentRepository.observeAll(),
        reminderRepository.observePending(),
        checklistRepository.observeAll(),
        agenda,
    ) { notes, intents, reminders, checklist, agendaSnapshot ->
        buildState(notes, intents, reminders, checklist, agendaSnapshot)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(dateLabel = dateLabel),
    )

    private fun buildState(
        notes: List<VoiceNote>,
        intents: Map<String, ParsedIntent>,
        reminders: List<Reminder>,
        checklist: List<ChecklistItem>,
        agendaSnapshot: AgendaSnapshot,
    ): HomeUiState {
        val zone = ZoneId.systemDefault()
        // observePending ya viene ordenado por instante ascendente.
        val next = reminders.firstOrNull()?.let { reminder ->
            DayReminder(
                timeLabel = formatOperatorDateTime(reminder.triggerAt, zone),
                message = reminder.message,
            )
        }
        val today = timeProvider.today()
        val feed = notes
            .filter { it.createdAt.atZone(zone).toLocalDate() == today }
            .sortedByDescending { it.createdAt }
            .take(FEED_MAX_ITEMS)
            .map { note ->
                FeedItem(
                    time = formatOperatorTime(note.createdAt, zone),
                    tag = intents[note.id]?.intentType?.name ?: note.status.name,
                    text = note.transcript,
                )
            }
        val now = timeProvider.now()
        val nextEvent = agendaSnapshot.events
            .filter { !it.allDay && it.startAt.isAfter(now) }
            .minByOrNull { it.startAt }
        return HomeUiState(
            dateLabel = dateLabel,
            noteCount = notes.size,
            nextReminder = next,
            pendingReminders = reminders.size,
            awaitingReview = intents.values.count { it.needsConfirmation },
            openActions = checklist.count { !it.done },
            feed = feed,
            calendarConnected = agendaSnapshot.hasPermission,
            nextEventLabel = nextEvent?.let { "${formatOperatorTime(it.startAt, zone)} ${it.title}" },
            eventsToday = agendaSnapshot.events.size,
        )
    }
}
