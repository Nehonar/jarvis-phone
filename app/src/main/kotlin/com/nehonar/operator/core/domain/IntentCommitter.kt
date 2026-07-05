package com.nehonar.operator.core.domain

import com.nehonar.operator.core.ai.ParsedIntent
import com.nehonar.operator.core.ai.ReminderTrigger
import com.nehonar.operator.core.common.TimeProvider
import com.nehonar.operator.core.domain.model.ChecklistItem
import com.nehonar.operator.core.domain.model.MemoryFact
import com.nehonar.operator.core.domain.model.PlaceReminder
import com.nehonar.operator.core.domain.model.Reminder
import com.nehonar.operator.core.domain.model.ReminderStatus
import com.nehonar.operator.core.domain.repository.ChecklistRepository
import com.nehonar.operator.core.domain.repository.MemoryRepository
import com.nehonar.operator.core.domain.repository.ParsedIntentRepository
import com.nehonar.operator.core.domain.repository.PlaceReminderRepository
import com.nehonar.operator.core.domain.repository.PlaceRepository
import com.nehonar.operator.core.domain.repository.ReminderRepository
import com.nehonar.operator.core.location.GeofenceScheduler
import com.nehonar.operator.core.notifications.ReminderScheduler
import com.nehonar.operator.core.widget.WidgetRefresher
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeParseException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persiste una intención confirmada: programa el recordatorio si tiene fecha/hora
 * futuras, crea los items de checklist, guarda los hechos memorables y registra
 * los recordatorios por lugar (con su geofence). Refresca el widget.
 *
 * Lógica compartida entre la revisión manual (ReviewViewModel) y la conversación
 * (ConversationViewModel), para no duplicarla y poder testearla una sola vez.
 */
@Singleton
class IntentCommitter @Inject constructor(
    private val parsedIntentRepository: ParsedIntentRepository,
    private val reminderRepository: ReminderRepository,
    private val reminderScheduler: ReminderScheduler,
    private val timeProvider: TimeProvider,
    private val widgetRefresher: WidgetRefresher,
    private val checklistRepository: ChecklistRepository,
    private val memoryRepository: MemoryRepository,
    private val placeRepository: PlaceRepository,
    private val placeReminderRepository: PlaceReminderRepository,
    private val geofenceScheduler: GeofenceScheduler,
) {

    suspend fun commit(voiceNoteId: String, intent: ParsedIntent) {
        parsedIntentRepository.save(voiceNoteId, intent.copy(needsConfirmation = false))
        scheduleReminderIfResolved(voiceNoteId, intent)
        saveChecklistItems(voiceNoteId, intent)
        saveMemoryFacts(intent)
        savePlaceReminders(voiceNoteId, intent)
    }

    private suspend fun scheduleReminderIfResolved(voiceNoteId: String, intent: ParsedIntent) {
        val triggerAt = resolveTriggerInstant(intent) ?: return
        if (!triggerAt.isAfter(timeProvider.now())) return
        val message = intent.reminders.firstOrNull()?.message ?: intent.assistantResponse
        val reminder = Reminder(
            id = UUID.randomUUID().toString(),
            voiceNoteId = voiceNoteId,
            message = message,
            triggerAt = triggerAt,
            status = ReminderStatus.PENDING,
        )
        reminderRepository.save(reminder)
        reminderScheduler.schedule(reminder)
        widgetRefresher.refresh()
    }

    private fun resolveTriggerInstant(intent: ParsedIntent): Instant? {
        val date = intent.date ?: return null
        val time = intent.time ?: return null
        return try {
            LocalDateTime.of(LocalDate.parse(date), LocalTime.parse(time))
                .atZone(ZoneId.systemDefault())
                .toInstant()
        } catch (e: DateTimeParseException) {
            null
        }
    }

    private suspend fun saveChecklistItems(voiceNoteId: String, intent: ParsedIntent) {
        intent.actions.forEach { action ->
            checklistRepository.save(
                ChecklistItem(
                    id = UUID.randomUUID().toString(),
                    voiceNoteId = voiceNoteId,
                    type = action.type,
                    label = action.label,
                    done = false,
                    createdAt = timeProvider.now(),
                ),
            )
        }
    }

    private suspend fun saveMemoryFacts(intent: ParsedIntent) {
        intent.memoryFacts.forEach { draft ->
            memoryRepository.save(
                MemoryFact(
                    id = UUID.randomUUID().toString(),
                    topic = draft.topic,
                    fact = draft.fact,
                    createdAt = timeProvider.now(),
                ),
            )
        }
    }

    /**
     * Cada recordatorio NEAR_LOCATION cuya etiqueta coincide con un lugar guardado
     * se persiste como PlaceReminder y (re)registra su geofence. Si el lugar no
     * existe aún, se ignora en silencio (la IA ya debería haberlo pedido).
     */
    private suspend fun savePlaceReminders(voiceNoteId: String, intent: ParsedIntent) {
        val locationDrafts = intent.reminders.filter {
            it.trigger == ReminderTrigger.NEAR_LOCATION && it.place != null
        }
        if (locationDrafts.isEmpty()) return
        val placesByLabel = placeRepository.getAll().associateBy { it.label.lowercase() }
        locationDrafts.forEach { draft ->
            val place = placesByLabel[draft.place!!.lowercase()] ?: return@forEach
            placeReminderRepository.save(
                PlaceReminder(
                    id = UUID.randomUUID().toString(),
                    voiceNoteId = voiceNoteId,
                    message = draft.message,
                    placeId = place.id,
                    placeLabel = place.label,
                    status = ReminderStatus.PENDING,
                    createdAt = timeProvider.now(),
                ),
            )
            geofenceScheduler.register(place)
        }
    }
}
