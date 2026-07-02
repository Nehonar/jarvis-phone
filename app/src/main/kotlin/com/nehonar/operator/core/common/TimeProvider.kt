package com.nehonar.operator.core.common

import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

interface TimeProvider {
    fun now(): Instant
    fun today(): LocalDate
}

class SystemTimeProvider @Inject constructor() : TimeProvider {
    override fun now(): Instant = Instant.now()
    override fun today(): LocalDate = LocalDate.now()
}
