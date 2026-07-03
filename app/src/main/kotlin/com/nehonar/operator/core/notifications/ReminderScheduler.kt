package com.nehonar.operator.core.notifications

import com.nehonar.operator.core.domain.model.Reminder

interface ReminderScheduler {
    fun schedule(reminder: Reminder)
    fun cancel(reminderId: String)
}
