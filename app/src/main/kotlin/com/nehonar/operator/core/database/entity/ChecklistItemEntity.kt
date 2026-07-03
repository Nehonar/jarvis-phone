package com.nehonar.operator.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.nehonar.operator.core.ai.ActionType
import com.nehonar.operator.core.domain.model.ChecklistItem
import java.time.Instant

@Entity(tableName = "checklist_items")
data class ChecklistItemEntity(
    @PrimaryKey val id: String,
    val voiceNoteId: String,
    val type: String,
    val label: String,
    val done: Boolean,
    val createdAtEpochMillis: Long,
)

fun ChecklistItemEntity.toDomain(): ChecklistItem = ChecklistItem(
    id = id,
    voiceNoteId = voiceNoteId,
    type = ActionType.entries.firstOrNull { it.name == type } ?: ActionType.OTHER,
    label = label,
    done = done,
    createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
)

fun ChecklistItem.toEntity(): ChecklistItemEntity = ChecklistItemEntity(
    id = id,
    voiceNoteId = voiceNoteId,
    type = type.name,
    label = label,
    done = done,
    createdAtEpochMillis = createdAt.toEpochMilli(),
)
