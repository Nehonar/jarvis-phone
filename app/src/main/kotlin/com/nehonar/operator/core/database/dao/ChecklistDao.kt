package com.nehonar.operator.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.nehonar.operator.core.database.entity.ChecklistItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChecklistDao {

    @Upsert
    suspend fun upsert(item: ChecklistItemEntity)

    @Query("SELECT * FROM checklist_items ORDER BY done ASC, createdAtEpochMillis DESC")
    fun observeAll(): Flow<List<ChecklistItemEntity>>

    @Query("SELECT * FROM checklist_items WHERE id = :id")
    suspend fun getById(id: String): ChecklistItemEntity?

    @Query("DELETE FROM checklist_items WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM checklist_items WHERE done = 1")
    suspend fun deleteDone()

    @Query("DELETE FROM checklist_items WHERE voiceNoteId = :voiceNoteId")
    suspend fun deleteForVoiceNote(voiceNoteId: String)
}
