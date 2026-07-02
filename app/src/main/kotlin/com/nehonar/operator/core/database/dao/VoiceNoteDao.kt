package com.nehonar.operator.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.nehonar.operator.core.database.entity.VoiceNoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VoiceNoteDao {

    @Upsert
    suspend fun upsert(note: VoiceNoteEntity)

    @Query("SELECT * FROM voice_notes ORDER BY createdAtEpochMillis DESC")
    fun observeAll(): Flow<List<VoiceNoteEntity>>

    @Query("SELECT * FROM voice_notes WHERE id = :id")
    suspend fun getById(id: String): VoiceNoteEntity?

    @Query("DELETE FROM voice_notes WHERE id = :id")
    suspend fun deleteById(id: String)
}
