package com.nehonar.operator.core.domain.repository

import com.nehonar.operator.core.domain.model.ChecklistItem
import kotlinx.coroutines.flow.Flow

interface ChecklistRepository {
    fun observeAll(): Flow<List<ChecklistItem>>
    suspend fun getById(id: String): ChecklistItem?
    suspend fun save(item: ChecklistItem)
    suspend fun delete(id: String)
    suspend fun deleteDone()
}
