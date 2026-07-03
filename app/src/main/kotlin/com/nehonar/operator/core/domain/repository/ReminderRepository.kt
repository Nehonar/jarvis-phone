package com.nehonar.operator.core.domain.repository

import com.nehonar.operator.core.domain.model.Reminder
import kotlinx.coroutines.flow.Flow

interface ReminderRepository {
    fun observePending(): Flow<List<Reminder>>
    suspend fun getById(id: String): Reminder?
    suspend fun getAllPending(): List<Reminder>
    suspend fun getAllForVoiceNote(voiceNoteId: String): List<Reminder>
    suspend fun save(reminder: Reminder)
    suspend fun delete(id: String)
}
