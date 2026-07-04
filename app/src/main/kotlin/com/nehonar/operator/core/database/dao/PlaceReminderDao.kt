package com.nehonar.operator.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.nehonar.operator.core.database.entity.PlaceReminderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaceReminderDao {

    @Upsert
    suspend fun upsert(reminder: PlaceReminderEntity)

    @Query("SELECT * FROM place_reminders WHERE status = :status ORDER BY createdAtEpochMillis DESC")
    fun observeByStatus(status: String): Flow<List<PlaceReminderEntity>>

    @Query("SELECT * FROM place_reminders WHERE status = :status")
    suspend fun getAllByStatus(status: String): List<PlaceReminderEntity>

    @Query("SELECT * FROM place_reminders WHERE id = :id")
    suspend fun getById(id: String): PlaceReminderEntity?

    @Query("SELECT * FROM place_reminders WHERE placeId = :placeId")
    suspend fun getAllForPlace(placeId: String): List<PlaceReminderEntity>

    @Query("DELETE FROM place_reminders WHERE id = :id")
    suspend fun deleteById(id: String)
}
