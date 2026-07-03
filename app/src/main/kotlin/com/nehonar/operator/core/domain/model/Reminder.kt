package com.nehonar.operator.core.domain.model

import java.time.Instant

enum class ReminderStatus {
    PENDING,
    DONE,
    DISMISSED,
}

data class Reminder(
    val id: String,
    val voiceNoteId: String,
    val message: String,
    val triggerAt: Instant,
    val status: ReminderStatus,
)
