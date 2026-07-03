package com.nehonar.operator.core.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nehonar.operator.core.common.TimeProvider
import com.nehonar.operator.core.domain.repository.ReminderRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * AlarmManager no sobrevive a un reinicio: al recibir BOOT_COMPLETED se
 * reprograman los recordatorios PENDING cuyo instante aún no ha pasado.
 */
@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() {

    @Inject lateinit var reminderRepository: ReminderRepository

    @Inject lateinit var reminderScheduler: ReminderScheduler

    @Inject lateinit var timeProvider: TimeProvider

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val now = timeProvider.now()
                reminderRepository.getAllPending()
                    .filter { it.triggerAt.isAfter(now) }
                    .forEach { reminderScheduler.schedule(it) }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
