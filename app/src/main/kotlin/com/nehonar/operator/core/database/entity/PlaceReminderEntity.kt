package com.nehonar.operator.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.nehonar.operator.core.domain.model.PlaceReminder
import com.nehonar.operator.core.domain.model.ReminderStatus
import java.time.Instant

@Entity(tableName = "place_reminders")
data class PlaceReminderEntity(
    @PrimaryKey val id: String,
    val voiceNoteId: String,
    val message: String,
    val placeId: String,
    val placeLabel: String,
    val status: String,
    val createdAtEpochMillis: Long,
)

fun PlaceReminderEntity.toDomain(): PlaceReminder = PlaceReminder(
    id = id,
    voiceNoteId = voiceNoteId,
    message = message,
    placeId = placeId,
    placeLabel = placeLabel,
    status = ReminderStatus.entries.firstOrNull { it.name == status } ?: ReminderStatus.DISMISSED,
    createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
)

fun PlaceReminder.toEntity(): PlaceReminderEntity = PlaceReminderEntity(
    id = id,
    voiceNoteId = voiceNoteId,
    message = message,
    placeId = placeId,
    placeLabel = placeLabel,
    status = status.name,
    createdAtEpochMillis = createdAt.toEpochMilli(),
)
