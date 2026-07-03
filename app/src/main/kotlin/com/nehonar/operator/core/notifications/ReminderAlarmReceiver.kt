package com.nehonar.operator.core.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ReminderAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getStringExtra(AlarmManagerReminderScheduler.EXTRA_REMINDER_ID) ?: return
        val message = intent.getStringExtra(AlarmManagerReminderScheduler.EXTRA_REMINDER_MESSAGE).orEmpty()
        ReminderNotifier.notify(context, reminderId, message)
    }
}
