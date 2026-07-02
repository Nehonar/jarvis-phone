package com.nehonar.operator.core.database.entity

import com.nehonar.operator.core.domain.model.VoiceNote
import com.nehonar.operator.core.domain.model.VoiceNoteStatus
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceNoteMapperTest {

    @Test
    fun `entity a dominio y vuelta conserva todos los campos`() {
        val note = VoiceNote(
            id = "n1",
            audioUri = "file:///audio/n1.m4a",
            transcript = "mañana oficina, llevar portátil y cargador",
            createdAt = Instant.ofEpochMilli(1_750_000_000_000),
            status = VoiceNoteStatus.TRANSCRIBED,
        )
        assertEquals(note, note.toEntity().toDomain())
    }

    @Test
    fun `audioUri nulo se conserva`() {
        val note = VoiceNote(
            id = "n2",
            audioUri = null,
            transcript = "comprar fruta",
            createdAt = Instant.ofEpochMilli(0),
            status = VoiceNoteStatus.PENDING,
        )
        assertEquals(note, note.toEntity().toDomain())
    }

    @Test
    fun `status desconocido en base de datos mapea a FAILED`() {
        val entity = VoiceNoteEntity(
            id = "n3",
            audioUri = null,
            transcript = "x",
            createdAtEpochMillis = 0,
            status = "ESTADO_FUTURO",
        )
        assertEquals(VoiceNoteStatus.FAILED, entity.toDomain().status)
    }
}
