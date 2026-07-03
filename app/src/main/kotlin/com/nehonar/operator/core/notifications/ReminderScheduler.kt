package com.nehonar.operator.core.notifications

import com.nehonar.operator.core.domain.model.Reminder

interface ReminderScheduler {
    fun schedule(reminder: Reminder)
    fun cancel(reminderId: String)

    /** `false` en Android 12+ sin el permiso especial de "alarmas y recordatorios". */
    fun canScheduleExact(): Boolean
}
