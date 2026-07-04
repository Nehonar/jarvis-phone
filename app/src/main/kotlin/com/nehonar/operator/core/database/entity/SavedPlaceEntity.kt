package com.nehonar.operator.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.nehonar.operator.core.domain.model.SavedPlace
import java.time.Instant

@Entity(tableName = "saved_places")
data class SavedPlaceEntity(
    @PrimaryKey val id: String,
    val label: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float,
    val createdAtEpochMillis: Long,
)

fun SavedPlaceEntity.toDomain(): SavedPlace = SavedPlace(
    id = id,
    label = label,
    latitude = latitude,
    longitude = longitude,
    radiusMeters = radiusMeters,
    createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
)

fun SavedPlace.toEntity(): SavedPlaceEntity = SavedPlaceEntity(
    id = id,
    label = label,
    latitude = latitude,
    longitude = longitude,
    radiusMeters = radiusMeters,
    createdAtEpochMillis = createdAt.toEpochMilli(),
)
