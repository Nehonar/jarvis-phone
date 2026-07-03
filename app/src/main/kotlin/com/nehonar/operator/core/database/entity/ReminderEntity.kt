package com.nehonar.operator.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.nehonar.operator.core.domain.model.Reminder
import com.nehonar.operator.core.domain.model.ReminderStatus
import java.time.Instant

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey val id: String,
    val voiceNoteId: String,
    val message: String,
    val triggerAtEpochMillis: Long,
    val status: String,
)

fun ReminderEntity.toDomain(): Reminder = Reminder(
    id = id,
    voiceNoteId = voiceNoteId,
    message = message,
    triggerAt = Instant.ofEpochMilli(triggerAtEpochMillis),
    status = ReminderStatus.entries.firstOrNull { it.name == status } ?: ReminderStatus.DISMISSED,
)

fun Reminder.toEntity(): ReminderEntity = ReminderEntity(
    id = id,
    voiceNoteId = voiceNoteId,
    message = message,
    triggerAtEpochMillis = triggerAt.toEpochMilli(),
    status = status.name,
)
