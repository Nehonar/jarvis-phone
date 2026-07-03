package com.nehonar.operator.core.database

import com.nehonar.operator.core.database.dao.ReminderDao
import com.nehonar.operator.core.database.entity.toDomain
import com.nehonar.operator.core.database.entity.toEntity
import com.nehonar.operator.core.domain.model.Reminder
import com.nehonar.operator.core.domain.model.ReminderStatus
import com.nehonar.operator.core.domain.repository.ReminderRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ReminderRepositoryImpl @Inject constructor(
    private val dao: ReminderDao,
) : ReminderRepository {

    override fun observePending(): Flow<List<Reminder>> =
        dao.observeByStatus(ReminderStatus.PENDING.name).map { list -> list.map { it.toDomain() } }

    override suspend fun getById(id: String): Reminder? = dao.getById(id)?.toDomain()

    override suspend fun getAllPending(): List<Reminder> =
        dao.getAllByStatus(ReminderStatus.PENDING.name).map { it.toDomain() }

    override suspend fun save(reminder: Reminder) {
        dao.upsert(reminder.toEntity())
    }

    override suspend fun delete(id: String) {
        dao.deleteById(id)
    }
}
