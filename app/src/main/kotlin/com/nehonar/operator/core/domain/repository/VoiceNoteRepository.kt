package com.nehonar.operator.core.domain.repository

import com.nehonar.operator.core.domain.model.VoiceNote
import kotlinx.coroutines.flow.Flow

interface VoiceNoteRepository {
    fun observeAll(): Flow<List<VoiceNote>>
    suspend fun save(note: VoiceNote)
    suspend fun delete(id: String)
}
