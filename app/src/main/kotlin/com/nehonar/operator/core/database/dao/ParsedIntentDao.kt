package com.nehonar.operator.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.nehonar.operator.core.database.entity.ParsedIntentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ParsedIntentDao {

    @Upsert
    suspend fun upsert(intent: ParsedIntentEntity)

    @Query("SELECT * FROM parsed_intents")
    fun observeAll(): Flow<List<ParsedIntentEntity>>

    @Query("SELECT * FROM parsed_intents WHERE voiceNoteId = :voiceNoteId")
    suspend fun getByVoiceNoteId(voiceNoteId: String): ParsedIntentEntity?

    @Query("SELECT * FROM parsed_intents WHERE voiceNoteId = :voiceNoteId")
    fun observeByVoiceNoteId(voiceNoteId: String): Flow<ParsedIntentEntity?>

    @Query("DELETE FROM parsed_intents WHERE voiceNoteId = :voiceNoteId")
    suspend fun deleteByVoiceNoteId(voiceNoteId: String)
}
