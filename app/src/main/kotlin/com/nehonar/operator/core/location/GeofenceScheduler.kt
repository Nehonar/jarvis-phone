package com.nehonar.operator.core.location

import com.nehonar.operator.core.domain.model.SavedPlace

/**
 * Registra/retira geofences en el sistema. Implementación con Play Services, solo
 * dispositivo (no testeable en JVM, ver docs/fase-13-plan.md).
 */
interface GeofenceScheduler {
    /** `false` sin permiso de ubicación en segundo plano en Android 10+. */
    fun canRegisterGeofences(): Boolean
    fun register(place: SavedPlace)
    fun unregister(placeId: String)
}
