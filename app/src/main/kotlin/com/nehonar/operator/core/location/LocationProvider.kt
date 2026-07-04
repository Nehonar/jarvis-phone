package com.nehonar.operator.core.location

data class LatLng(val latitude: Double, val longitude: Double)

/** Obtiene la posición actual una vez (para guardar un lugar). Null si no hay permiso o fix. */
interface LocationProvider {
    fun hasLocationPermission(): Boolean
    suspend fun currentLocation(): LatLng?
}
