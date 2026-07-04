package com.nehonar.operator.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.nehonar.operator.core.domain.model.MemoryFact
import java.time.Instant

@Entity(tableName = "memory_facts")
data class MemoryFactEntity(
    @PrimaryKey val id: String,
    val topic: String,
    val fact: String,
    val createdAtEpochMillis: Long,
)

fun MemoryFactEntity.toDomain(): MemoryFact = MemoryFact(
    id = id,
    topic = topic,
    fact = fact,
    createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
)

fun MemoryFact.toEntity(): MemoryFactEntity = MemoryFactEntity(
    id = id,
    topic = topic,
    fact = fact,
    createdAtEpochMillis = createdAt.toEpochMilli(),
)
