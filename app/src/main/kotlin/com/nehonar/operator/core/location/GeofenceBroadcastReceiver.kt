package com.nehonar.operator.core.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.GeofencingEvent
import com.nehonar.operator.core.domain.model.ReminderStatus
import com.nehonar.operator.core.domain.repository.PlaceReminderRepository
import com.nehonar.operator.core.notifications.ReminderNotifier
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Salta al ENTRAR en un geofence. Cada geofence lleva por requestId el id del
 * lugar guardado; se notifican los recordatorios PENDING de ese lugar.
 */
@AndroidEntryPoint
class GeofenceBroadcastReceiver : BroadcastReceiver() {

    @Inject lateinit var placeReminderRepository: PlaceReminderRepository

    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) return
        val placeIds = event.triggeringGeofences?.map { it.requestId } ?: return
        if (placeIds.isEmpty()) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                placeIds.forEach { placeId ->
                    placeReminderRepository.getAllForPlace(placeId)
                        .filter { it.status == ReminderStatus.PENDING }
                        .forEach { reminder ->
                            ReminderNotifier.notify(context, reminder.id, reminder.message)
                        }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
