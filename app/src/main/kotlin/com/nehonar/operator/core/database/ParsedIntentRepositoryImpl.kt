package com.nehonar.operator.core.database

import com.nehonar.operator.core.ai.IntentType
import com.nehonar.operator.core.ai.ParsedIntent
import com.nehonar.operator.core.common.TimeProvider
import com.nehonar.operator.core.database.dao.ParsedIntentDao
import com.nehonar.operator.core.database.entity.toDomain
import com.nehonar.operator.core.database.entity.toEntity
import com.nehonar.operator.core.domain.repository.ParsedIntentRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ParsedIntentRepositoryImpl @Inject constructor(
    private val dao: ParsedIntentDao,
    private val timeProvider: TimeProvider,
) : ParsedIntentRepository {

    override fun observeByVoiceNoteId(voiceNoteId: String): Flow<ParsedIntent?> =
        dao.observeByVoiceNoteId(voiceNoteId).map { it?.toDomain() }

    override suspend fun getByVoiceNoteId(voiceNoteId: String): ParsedIntent? =
        dao.getByVoiceNoteId(voiceNoteId)?.toDomain()

    override suspend fun save(voiceNoteId: String, intent: ParsedIntent) {
        dao.upsert(intent.toEntity(voiceNoteId, timeProvider.now()))
    }

    override suspend fun deleteByVoiceNoteId(voiceNoteId: String) {
        dao.deleteByVoiceNoteId(voiceNoteId)
    }

    override fun observeIntentTypesByVoiceNoteId(): Flow<Map<String, IntentType>> =
        dao.observeAll().map { entities -> entities.associate { it.voiceNoteId to it.toDomain().intentType } }

    override fun observeAll(): Flow<Map<String, ParsedIntent>> =
        dao.observeAll().map { entities -> entities.associate { it.voiceNoteId to it.toDomain() } }
}
