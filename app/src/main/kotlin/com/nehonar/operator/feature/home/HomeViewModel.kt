package com.nehonar.operator.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nehonar.operator.core.ai.ParsedIntent
import com.nehonar.operator.core.common.TimeProvider
import com.nehonar.operator.core.common.formatOperatorDate
import com.nehonar.operator.core.common.formatOperatorDateTime
import com.nehonar.operator.core.common.formatOperatorTime
import com.nehonar.operator.core.domain.model.Reminder
import com.nehonar.operator.core.domain.model.VoiceNote
import com.nehonar.operator.core.domain.repository.ParsedIntentRepository
import com.nehonar.operator.core.domain.repository.ReminderRepository
import com.nehonar.operator.core.domain.repository.VoiceNoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

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
    val feed: List<FeedItem> = emptyList(),
)

private const val FEED_MAX_ITEMS = 4

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val timeProvider: TimeProvider,
    voiceNoteRepository: VoiceNoteRepository,
    parsedIntentRepository: ParsedIntentRepository,
    reminderRepository: ReminderRepository,
) : ViewModel() {

    private val dateLabel = formatOperatorDate(timeProvider.today())

    val uiState: StateFlow<HomeUiState> = combine(
        voiceNoteRepository.observeAll(),
        parsedIntentRepository.observeAll(),
        reminderRepository.observePending(),
    ) { notes, intents, reminders ->
        buildState(notes, intents, reminders)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(dateLabel = dateLabel),
    )

    private fun buildState(
        notes: List<VoiceNote>,
        intents: Map<String, ParsedIntent>,
        reminders: List<Reminder>,
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
        return HomeUiState(
            dateLabel = dateLabel,
            noteCount = notes.size,
            nextReminder = next,
            pendingReminders = reminders.size,
            awaitingReview = intents.values.count { it.needsConfirmation },
            feed = feed,
        )
    }
}
