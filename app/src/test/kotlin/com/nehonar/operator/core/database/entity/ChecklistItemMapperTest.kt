package com.nehonar.operator.core.database.entity

import com.nehonar.operator.core.ai.ActionType
import com.nehonar.operator.core.domain.model.ChecklistItem
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class ChecklistItemMapperTest {

    @Test
    fun `entity a dominio y vuelta conserva todos los campos`() {
        val item = ChecklistItem(
            id = "c1",
            voiceNoteId = "n1",
            type = ActionType.CARRY,
            label = "el portátil",
            done = true,
            createdAt = Instant.ofEpochMilli(1_750_000_000_000),
        )
        assertEquals(item, item.toEntity().toDomain())
    }

    @Test
    fun `type desconocido en base de datos mapea a OTHER`() {
        val entity = ChecklistItemEntity(
            id = "c2",
            voiceNoteId = "n1",
            type = "TIPO_FUTURO",
            label = "x",
            done = false,
            createdAtEpochMillis = 0,
        )
        assertEquals(ActionType.OTHER, entity.toDomain().type)
    }
}
