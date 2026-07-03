package com.nehonar.operator.core.domain.model

import com.nehonar.operator.core.ai.ActionType
import java.time.Instant

data class ChecklistItem(
    val id: String,
    val voiceNoteId: String,
    val type: ActionType,
    val label: String,
    val done: Boolean,
    val createdAt: Instant,
)
