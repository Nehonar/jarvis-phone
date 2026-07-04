package com.nehonar.operator.core.domain.repository

import com.nehonar.operator.core.domain.model.SavedPlace
import kotlinx.coroutines.flow.Flow

interface PlaceRepository {
    fun observeAll(): Flow<List<SavedPlace>>
    suspend fun getAll(): List<SavedPlace>
    suspend fun getById(id: String): SavedPlace?
    suspend fun save(place: SavedPlace)
    suspend fun delete(id: String)
}
