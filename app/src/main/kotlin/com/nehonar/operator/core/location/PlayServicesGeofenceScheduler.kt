package com.nehonar.operator.core.location

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.nehonar.operator.core.domain.model.SavedPlace
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Registro real de geofences con Play Services. Solo dispositivo (ver riesgo 1 de
 * docs/fase-13-plan.md). El geofence dispara [GeofenceBroadcastReceiver] al ENTRAR.
 */
class PlayServicesGeofenceScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : GeofenceScheduler {

    private val client: GeofencingClient by lazy { LocationServices.getGeofencingClient(context) }

    override fun canRegisterGeofences(): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        // El geofence en segundo plano necesita ACCESS_BACKGROUND_LOCATION en Android 10+.
        val background = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        return fine && background
    }

    override fun register(place: SavedPlace) {
        if (!canRegisterGeofences()) {
            Log.w(TAG, "Geofence NO registrado para '${place.label}': falta permiso de ubicación en segundo plano")
            return
        }
        val geofence = Geofence.Builder()
            .setRequestId(place.id)
            .setCircularRegion(place.latitude, place.longitude, place.radiusMeters)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
            .build()
        val request = GeofencingRequest.Builder()
            .setInitialTrigger(0)
            .addGeofence(geofence)
            .build()
        try {
            client.addGeofences(request, pendingIntent())
                .addOnSuccessListener { Log.i(TAG, "Geofence registrado: '${place.label}' (${place.id})") }
                .addOnFailureListener { e ->
                    // Fallo típico: ubicación desactivada, demasiados geofences, o
                    // Play Services no disponible. Antes se perdía en silencio.
                    Log.e(TAG, "Fallo al registrar geofence '${place.label}': ${e.message}", e)
                }
        } catch (e: SecurityException) {
            // El permiso pudo revocarse entre la comprobación y aquí: no bloquea.
            Log.e(TAG, "SecurityException al registrar geofence '${place.label}'", e)
        }
    }

    override fun unregister(placeId: String) {
        client.removeGeofences(listOf(placeId))
    }

    private fun pendingIntent(): PendingIntent {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
    }

    private companion object {
        const val TAG = "GeofenceScheduler"
    }
}
