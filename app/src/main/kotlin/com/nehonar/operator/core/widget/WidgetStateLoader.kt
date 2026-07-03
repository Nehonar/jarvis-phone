package com.nehonar.operator.core.widget

import com.nehonar.operator.core.common.formatOperatorDateTime
import com.nehonar.operator.core.domain.repository.ReminderRepository
import java.time.ZoneId
import javax.inject.Inject

data class OperatorWidgetState(
    val nextTimeLabel: String?,
    val nextMessage: String?,
    val pendingCount: Int,
)

class WidgetStateLoader @Inject constructor(
    private val reminderRepository: ReminderRepository,
) {

    suspend fun load(zone: ZoneId = ZoneId.systemDefault()): OperatorWidgetState {
        val pending = reminderRepository.getAllPending()
        val next = pending.minByOrNull { it.triggerAt }
        return OperatorWidgetState(
            nextTimeLabel = next?.let { formatOperatorDateTime(it.triggerAt, zone) },
            nextMessage = next?.message,
            pendingCount = pending.size,
        )
    }
}
