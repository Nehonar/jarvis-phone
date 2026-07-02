package com.nehonar.operator.core.domain.model

import java.time.Instant

data class VoiceNote(
    val id: String,
    val audioUri: String?,
    val transcript: String,
    val createdAt: Instant,
    val status: VoiceNoteStatus,
)
