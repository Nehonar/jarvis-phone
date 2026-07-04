package com.nehonar.operator.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.nehonar.operator.core.database.entity.SavedPlaceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaceDao {

    @Upsert
    suspend fun upsert(place: SavedPlaceEntity)

    @Query("SELECT * FROM saved_places ORDER BY createdAtEpochMillis DESC")
    fun observeAll(): Flow<List<SavedPlaceEntity>>

    @Query("SELECT * FROM saved_places")
    suspend fun getAll(): List<SavedPlaceEntity>

    @Query("SELECT * FROM saved_places WHERE id = :id")
    suspend fun getById(id: String): SavedPlaceEntity?

    @Query("DELETE FROM saved_places WHERE id = :id")
    suspend fun deleteById(id: String)
}
