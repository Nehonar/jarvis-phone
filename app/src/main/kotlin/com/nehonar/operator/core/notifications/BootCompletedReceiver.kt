package com.nehonar.operator.core.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nehonar.operator.core.common.TimeProvider
import com.nehonar.operator.core.domain.repository.PlaceRepository
import com.nehonar.operator.core.domain.repository.ReminderRepository
import com.nehonar.operator.core.location.GeofenceScheduler
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Ni las alarmas de AlarmManager ni los geofences sobreviven a un reinicio: al
 * recibir BOOT_COMPLETED se reprograman los recordatorios PENDING futuros y se
 * vuelven a registrar los geofences de los lugares guardados.
 */
@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() {

    @Inject lateinit var reminderRepository: ReminderRepository

    @Inject lateinit var reminderScheduler: ReminderScheduler

    @Inject lateinit var timeProvider: TimeProvider

    @Inject lateinit var placeRepository: PlaceRepository

    @Inject lateinit var geofenceScheduler: GeofenceScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val now = timeProvider.now()
                reminderRepository.getAllPending()
                    .filter { it.triggerAt.isAfter(now) }
                    .forEach { reminderScheduler.schedule(it) }
                if (geofenceScheduler.canRegisterGeofences()) {
                    placeRepository.getAll().forEach { geofenceScheduler.register(it) }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
