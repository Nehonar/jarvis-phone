package com.nehonar.operator.core.database

import com.nehonar.operator.core.database.dao.PlaceDao
import com.nehonar.operator.core.database.entity.toDomain
import com.nehonar.operator.core.database.entity.toEntity
import com.nehonar.operator.core.domain.model.SavedPlace
import com.nehonar.operator.core.domain.repository.PlaceRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PlaceRepositoryImpl @Inject constructor(
    private val dao: PlaceDao,
) : PlaceRepository {

    override fun observeAll(): Flow<List<SavedPlace>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getAll(): List<SavedPlace> = dao.getAll().map { it.toDomain() }

    override suspend fun getById(id: String): SavedPlace? = dao.getById(id)?.toDomain()

    override suspend fun save(place: SavedPlace) {
        dao.upsert(place.toEntity())
    }

    override suspend fun delete(id: String) {
        dao.deleteById(id)
    }
}
