package com.nehonar.operator.core.domain.model

import java.time.Instant

data class MemoryFact(
    val id: String,
    val topic: String,
    val fact: String,
    val createdAt: Instant,
)
