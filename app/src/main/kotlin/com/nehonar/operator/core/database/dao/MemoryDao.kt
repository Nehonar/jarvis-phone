package com.nehonar.operator.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.nehonar.operator.core.database.entity.MemoryFactEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {

    @Upsert
    suspend fun upsert(fact: MemoryFactEntity)

    @Query("SELECT * FROM memory_facts ORDER BY createdAtEpochMillis DESC")
    fun observeAll(): Flow<List<MemoryFactEntity>>

    @Query("SELECT * FROM memory_facts ORDER BY createdAtEpochMillis DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<MemoryFactEntity>

    @Query("DELETE FROM memory_facts WHERE id = :id")
    suspend fun deleteById(id: String)
}
