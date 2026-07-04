package com.nehonar.operator.core.domain.repository

import com.nehonar.operator.core.domain.model.MemoryFact
import kotlinx.coroutines.flow.Flow

interface MemoryRepository {
    /** Todos los hechos, más recientes primero. */
    fun observeAll(): Flow<List<MemoryFact>>

    /** Los [limit] hechos más recientes, para el contexto de la IA. */
    suspend fun getRecent(limit: Int): List<MemoryFact>

    suspend fun save(fact: MemoryFact)
    suspend fun delete(id: String)
}
