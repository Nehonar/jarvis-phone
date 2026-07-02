package com.nehonar.operator.testing

import com.nehonar.operator.core.common.TimeProvider
import com.nehonar.operator.core.domain.model.VoiceNote
import com.nehonar.operator.core.domain.repository.VoiceNoteRepository
import com.nehonar.operator.core.voice.SpeechToText
import com.nehonar.operator.core.voice.SttEvent
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class FakeSpeechToText(
    private val events: List<SttEvent>,
    private val available: Boolean = true,
) : SpeechToText {
    override fun isAvailable(): Boolean = available
    override fun listen(): Flow<SttEvent> = events.asFlow()
}

class FakeVoiceNoteRepository : VoiceNoteRepository {

    private val notes = MutableStateFlow<List<VoiceNote>>(emptyList())

    val current: List<VoiceNote> get() = notes.value

    override fun observeAll(): Flow<List<VoiceNote>> =
        notes.map { list -> list.sortedByDescending { it.createdAt } }

    override suspend fun save(note: VoiceNote) {
        notes.update { list -> list.filterNot { it.id == note.id } + note }
    }

    override suspend fun delete(id: String) {
        notes.update { list -> list.filterNot { it.id == id } }
    }
}

class FixedTimeProvider(
    private val instant: Instant = Instant.parse("2026-07-02T10:15:00Z"),
) : TimeProvider {
    override fun now(): Instant = instant
    override fun today(): LocalDate = instant.atZone(ZoneOffset.UTC).toLocalDate()
}
