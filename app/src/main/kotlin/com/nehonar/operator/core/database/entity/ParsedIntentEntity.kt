package com.nehonar.operator.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.nehonar.operator.core.ai.ActionItem
import com.nehonar.operator.core.ai.ClarifyingQuestion
import com.nehonar.operator.core.ai.IntentType
import com.nehonar.operator.core.ai.MemoryFactDraft
import com.nehonar.operator.core.ai.ParsedIntent
import com.nehonar.operator.core.ai.ReminderDraft
import java.time.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Entity(tableName = "parsed_intents")
data class ParsedIntentEntity(
    @PrimaryKey val voiceNoteId: String,
    val intentType: String,
    val confidence: Float,
    val title: String,
    val summary: String,
    val actionsJson: String,
    val remindersJson: String,
    val clarifyingQuestionsJson: String,
    val assistantResponse: String,
    val needsConfirmation: Boolean,
    val createdAtEpochMillis: Long,
    val date: String? = null,
    val time: String? = null,
    // defaultValue declarado para que la validación de esquema de Room cuadre
    // con el DEFAULT '[]' que aplica MIGRATION_4_5 al añadir la columna.
    @ColumnInfo(defaultValue = "'[]'")
    val memoryFactsJson: String = "[]",
)

private val json = Json { ignoreUnknownKeys = true }

fun ParsedIntentEntity.toDomain(): ParsedIntent = ParsedIntent(
    intentType = IntentType.entries.firstOrNull { it.name == intentType } ?: IntentType.UNKNOWN,
    confidence = confidence,
    title = title,
    summary = summary,
    actions = json.decodeFromString(actionsJson),
    reminders = json.decodeFromString(remindersJson),
    clarifyingQuestions = json.decodeFromString(clarifyingQuestionsJson),
    assistantResponse = assistantResponse,
    needsConfirmation = needsConfirmation,
    date = date,
    time = time,
    memoryFacts = json.decodeFromString(memoryFactsJson),
)

fun ParsedIntent.toEntity(voiceNoteId: String, createdAt: Instant): ParsedIntentEntity =
    ParsedIntentEntity(
        voiceNoteId = voiceNoteId,
        intentType = intentType.name,
        confidence = confidence,
        title = title,
        summary = summary,
        actionsJson = json.encodeToString<List<ActionItem>>(actions),
        remindersJson = json.encodeToString<List<ReminderDraft>>(reminders),
        clarifyingQuestionsJson = json.encodeToString<List<ClarifyingQuestion>>(clarifyingQuestions),
        assistantResponse = assistantResponse,
        needsConfirmation = needsConfirmation,
        createdAtEpochMillis = createdAt.toEpochMilli(),
        date = date,
        time = time,
        memoryFactsJson = json.encodeToString<List<MemoryFactDraft>>(memoryFacts),
    )
