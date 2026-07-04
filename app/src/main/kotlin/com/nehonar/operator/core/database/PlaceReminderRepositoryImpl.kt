package com.nehonar.operator.core.database

import com.nehonar.operator.core.database.dao.PlaceReminderDao
import com.nehonar.operator.core.database.entity.toDomain
import com.nehonar.operator.core.database.entity.toEntity
import com.nehonar.operator.core.domain.model.PlaceReminder
import com.nehonar.operator.core.domain.model.ReminderStatus
import com.nehonar.operator.core.domain.repository.PlaceReminderRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PlaceReminderRepositoryImpl @Inject constructor(
    private val dao: PlaceReminderDao,
) : PlaceReminderRepository {

    override fun observePending(): Flow<List<PlaceReminder>> =
        dao.observeByStatus(ReminderStatus.PENDING.name).map { list -> list.map { it.toDomain() } }

    override suspend fun getAllPending(): List<PlaceReminder> =
        dao.getAllByStatus(ReminderStatus.PENDING.name).map { it.toDomain() }

    override suspend fun getById(id: String): PlaceReminder? = dao.getById(id)?.toDomain()

    override suspend fun getAllForPlace(placeId: String): List<PlaceReminder> =
        dao.getAllForPlace(placeId).map { it.toDomain() }

    override suspend fun save(reminder: PlaceReminder) {
        dao.upsert(reminder.toEntity())
    }

    override suspend fun delete(id: String) {
        dao.deleteById(id)
    }
}
