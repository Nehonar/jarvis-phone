package com.nehonar.operator.core.database

import com.nehonar.operator.core.database.dao.VoiceNoteDao
import com.nehonar.operator.core.database.entity.VoiceNoteEntity
import com.nehonar.operator.core.database.entity.toDomain
import com.nehonar.operator.core.database.entity.toEntity
import com.nehonar.operator.core.domain.model.VoiceNote
import com.nehonar.operator.core.domain.repository.VoiceNoteRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class VoiceNoteRepositoryImpl @Inject constructor(
    private val dao: VoiceNoteDao,
) : VoiceNoteRepository {

    override fun observeAll(): Flow<List<VoiceNote>> =
        dao.observeAll().map { notes -> notes.map(VoiceNoteEntity::toDomain) }

    override suspend fun getById(id: String): VoiceNote? = dao.getById(id)?.toDomain()

    override suspend fun save(note: VoiceNote) {
        dao.upsert(note.toEntity())
    }

    override suspend fun delete(id: String) {
        dao.deleteById(id)
    }
}
