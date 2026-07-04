package com.nehonar.operator.core.domain.repository

import com.nehonar.operator.core.domain.model.PlaceReminder
import kotlinx.coroutines.flow.Flow

interface PlaceReminderRepository {
    fun observePending(): Flow<List<PlaceReminder>>
    suspend fun getAllPending(): List<PlaceReminder>
    suspend fun getById(id: String): PlaceReminder?
    suspend fun getAllForPlace(placeId: String): List<PlaceReminder>
    suspend fun save(reminder: PlaceReminder)
    suspend fun delete(id: String)
}
