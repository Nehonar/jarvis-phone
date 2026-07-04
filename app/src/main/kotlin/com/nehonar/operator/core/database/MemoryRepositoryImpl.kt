package com.nehonar.operator.core.database

import com.nehonar.operator.core.database.dao.MemoryDao
import com.nehonar.operator.core.database.entity.toDomain
import com.nehonar.operator.core.database.entity.toEntity
import com.nehonar.operator.core.domain.model.MemoryFact
import com.nehonar.operator.core.domain.repository.MemoryRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MemoryRepositoryImpl @Inject constructor(
    private val dao: MemoryDao,
) : MemoryRepository {

    override fun observeAll(): Flow<List<MemoryFact>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getRecent(limit: Int): List<MemoryFact> =
        dao.getRecent(limit).map { it.toDomain() }

    override suspend fun save(fact: MemoryFact) {
        dao.upsert(fact.toEntity())
    }

    override suspend fun delete(id: String) {
        dao.deleteById(id)
    }
}
