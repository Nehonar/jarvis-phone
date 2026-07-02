package com.nehonar.operator.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.nehonar.operator.core.domain.model.VoiceNote
import com.nehonar.operator.core.domain.model.VoiceNoteStatus
import java.time.Instant

@Entity(tableName = "voice_notes")
data class VoiceNoteEntity(
    @PrimaryKey val id: String,
    val audioUri: String?,
    val transcript: String,
    val createdAtEpochMillis: Long,
    val status: String,
)

fun VoiceNoteEntity.toDomain(): VoiceNote = VoiceNote(
    id = id,
    audioUri = audioUri,
    transcript = transcript,
    createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
    status = VoiceNoteStatus.entries.firstOrNull { it.name == status } ?: VoiceNoteStatus.FAILED,
)

fun VoiceNote.toEntity(): VoiceNoteEntity = VoiceNoteEntity(
    id = id,
    audioUri = audioUri,
    transcript = transcript,
    createdAtEpochMillis = createdAt.toEpochMilli(),
    status = status.name,
)
