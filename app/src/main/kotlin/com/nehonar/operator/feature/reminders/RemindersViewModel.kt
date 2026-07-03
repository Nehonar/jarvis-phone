package com.nehonar.operator.feature.reminders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nehonar.operator.core.common.formatOperatorDateTime
import com.nehonar.operator.core.domain.model.Reminder
import com.nehonar.operator.core.domain.model.ReminderStatus
import com.nehonar.operator.core.domain.repository.ReminderRepository
import com.nehonar.operator.core.notifications.ReminderScheduler
import com.nehonar.operator.core.widget.WidgetRefresher
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Duration
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ReminderItem(
    val id: String,
    val header: String,
    val message: String,
)

private val POSTPONE_DURATION: Duration = Duration.ofMinutes(15)

@HiltViewModel
class RemindersViewModel @Inject constructor(
    private val reminderRepository: ReminderRepository,
    private val reminderScheduler: ReminderScheduler,
    private val widgetRefresher: WidgetRefresher,
) : ViewModel() {

    val items: StateFlow<List<ReminderItem>> = reminderRepository.observePending()
        .map { list -> list.map { it.toItem() } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    private val _exactAlarmsEnabled = MutableStateFlow(reminderScheduler.canScheduleExact())
    val exactAlarmsEnabled: StateFlow<Boolean> = _exactAlarmsEnabled.asStateFlow()

    /** Se llama en ON_RESUME: el usuario puede volver de conceder el permiso en Ajustes. */
    fun refreshPermissions() {
        _exactAlarmsEnabled.value = reminderScheduler.canScheduleExact()
    }

    fun markDone(id: String) = updateStatus(id, ReminderStatus.DONE)

    fun dismiss(id: String) = updateStatus(id, ReminderStatus.DISMISSED)

    fun postpone(id: String) {
        viewModelScope.launch {
            val reminder = reminderRepository.getById(id) ?: return@launch
            val postponed = reminder.copy(triggerAt = reminder.triggerAt.plus(POSTPONE_DURATION))
            reminderRepository.save(postponed)
            reminderScheduler.schedule(postponed)
            widgetRefresher.refresh()
        }
    }

    private fun updateStatus(id: String, status: ReminderStatus) {
        viewModelScope.launch {
            val reminder = reminderRepository.getById(id) ?: return@launch
            reminderRepository.save(reminder.copy(status = status))
            reminderScheduler.cancel(id)
            widgetRefresher.refresh()
        }
    }
}

private fun Reminder.toItem(): ReminderItem = ReminderItem(
    id = id,
    header = formatOperatorDateTime(triggerAt, ZoneId.systemDefault()),
    message = message,
)
