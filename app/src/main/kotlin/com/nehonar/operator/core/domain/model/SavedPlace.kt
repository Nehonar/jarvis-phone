package com.nehonar.operator.core.domain.model

import java.time.Instant

data class SavedPlace(
    val id: String,
    /** Etiqueta con la que el usuario y la IA se refieren al lugar ("casa", "trabajo"). */
    val label: String,
    val latitude: Double,
    val longitude: Double,
    /** Radio del geofence en metros. */
    val radiusMeters: Float,
    val createdAt: Instant,
)
