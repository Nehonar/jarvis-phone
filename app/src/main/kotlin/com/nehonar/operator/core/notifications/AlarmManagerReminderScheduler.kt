package com.nehonar.operator.core.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.AlarmManagerCompat
import androidx.core.content.getSystemService
import com.nehonar.operator.core.domain.model.Reminder
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Envuelve AlarmManager. En Android 12+ (API 31) si no hay permiso de alarma
 * exacta cae a un aviso aproximado (`setAndAllowWhileIdle`) en vez de bloquear
 * la función — ver riesgo 1 en docs/fase-4-plan.md.
 */
class AlarmManagerReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : ReminderScheduler {

    private val alarmManager: AlarmManager
        get() = context.getSystemService()!!

    override fun schedule(reminder: Reminder) {
        val pendingIntent = pendingIntentFor(reminder.id, reminder.message)
        val triggerAtMillis = reminder.triggerAt.toEpochMilli()
        if (canScheduleExact()) {
            AlarmManagerCompat.setExactAndAllowWhileIdle(
                alarmManager,
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent,
            )
        } else {
            AlarmManagerCompat.setAndAllowWhileIdle(
                alarmManager,
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent,
            )
        }
    }

    override fun cancel(reminderId: String) {
        val pendingIntent = pendingIntentFor(reminderId, message = "")
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    override fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    private fun pendingIntentFor(reminderId: String, message: String): PendingIntent {
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ACTION_REMINDER_TRIGGER
            putExtra(EXTRA_REMINDER_ID, reminderId)
            putExtra(EXTRA_REMINDER_MESSAGE, message)
        }
        return PendingIntent.getBroadcast(
            context,
            reminderId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val ACTION_REMINDER_TRIGGER = "com.nehonar.operator.action.REMINDER_TRIGGER"
        const val EXTRA_REMINDER_ID = "reminder_id"
        const val EXTRA_REMINDER_MESSAGE = "reminder_message"
    }
}
