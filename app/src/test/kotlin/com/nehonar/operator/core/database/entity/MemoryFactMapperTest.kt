package com.nehonar.operator.core.database.entity

import com.nehonar.operator.core.domain.model.MemoryFact
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class MemoryFactMapperTest {

    @Test
    fun `entity a dominio y vuelta conserva todos los campos`() {
        val fact = MemoryFact(
            id = "m1",
            topic = "talla de pie",
            fact = "El usuario calza un 42",
            createdAt = Instant.ofEpochMilli(1_750_000_000_000),
        )
        assertEquals(fact, fact.toEntity().toDomain())
    }
}
