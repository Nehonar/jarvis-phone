package com.nehonar.operator.core.database

import com.nehonar.operator.core.database.dao.ChecklistDao
import com.nehonar.operator.core.database.entity.toDomain
import com.nehonar.operator.core.database.entity.toEntity
import com.nehonar.operator.core.domain.model.ChecklistItem
import com.nehonar.operator.core.domain.repository.ChecklistRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ChecklistRepositoryImpl @Inject constructor(
    private val dao: ChecklistDao,
) : ChecklistRepository {

    override fun observeAll(): Flow<List<ChecklistItem>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getById(id: String): ChecklistItem? = dao.getById(id)?.toDomain()

    override suspend fun save(item: ChecklistItem) {
        dao.upsert(item.toEntity())
    }

    override suspend fun delete(id: String) {
        dao.deleteById(id)
    }

    override suspend fun deleteDone() {
        dao.deleteDone()
    }

    override suspend fun deleteForVoiceNote(voiceNoteId: String) {
        dao.deleteForVoiceNote(voiceNoteId)
    }
}
