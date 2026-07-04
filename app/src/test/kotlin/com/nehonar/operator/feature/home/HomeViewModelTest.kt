package com.nehonar.operator.feature.home

import com.nehonar.operator.core.ai.ActionType
import com.nehonar.operator.core.ai.IntentType
import com.nehonar.operator.core.ai.ParsedIntent
import com.nehonar.operator.core.domain.model.ChecklistItem
import com.nehonar.operator.core.domain.model.Reminder
import com.nehonar.operator.core.domain.model.ReminderStatus
import com.nehonar.operator.core.domain.model.VoiceNote
import com.nehonar.operator.core.domain.model.VoiceNoteStatus
import com.nehonar.operator.core.calendar.CalendarEvent
import com.nehonar.operator.testing.FakeCalendarRepository
import com.nehonar.operator.testing.FakeChecklistRepository
import com.nehonar.operator.testing.FakeParsedIntentRepository
import com.nehonar.operator.testing.FakeReminderRepository
import com.nehonar.operator.testing.FakeVoiceNoteRepository
import com.nehonar.operator.testing.FixedTimeProvider
import com.nehonar.operator.testing.MainDispatcherRule
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // FixedTimeProvider: hoy = 2026-07-02 (UTC). Instantes a media mañana para no
    // depender de la zona horaria del runner (riesgo 1 de docs/fase-5-plan.md).
    private val timeProvider = FixedTimeProvider()
    private val voiceNoteRepository = FakeVoiceNoteRepository()
    private val parsedIntentRepository = FakeParsedIntentRepository()
    private val reminderRepository = FakeReminderRepository()
    private val checklistRepository = FakeChecklistRepository()
    private val calendarRepository = FakeCalendarRepository()

    private fun viewModel() = HomeViewModel(
        timeProvider = timeProvider,
        voiceNoteRepository = voiceNoteRepository,
        parsedIntentRepository = parsedIntentRepository,
        reminderRepository = reminderRepository,
        checklistRepository = checklistRepository,
        calendarRepository = calendarRepository,
    )

    private fun TestScope.collectState(vm: HomeViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect {}
        }
    }

    @Test
    fun `sin datos muestra el estado vacio`() = runTest {
        val vm = viewModel()
        collectState(vm)

        val state = vm.uiState.value
        assertNull(state.nextReminder)
        assertEquals(0, state.pendingReminders)
        assertEquals(0, state.awaitingReview)
        assertEquals(emptyList<FeedItem>(), state.feed)
    }

    @Test
    fun `el proximo recordatorio es el primero pendiente y cuenta el total`() = runTest {
        reminderRepository.save(reminder("late", "2026-07-02T15:00:00Z", "Recoger paquete"))
        reminderRepository.save(reminder("soon", "2026-07-02T11:00:00Z", "Salir de casa"))

        val vm = viewModel()
        collectState(vm)

        val state = vm.uiState.value
        assertEquals("Salir de casa", state.nextReminder?.message)
        assertEquals(2, state.pendingReminders)
    }

    @Test
    fun `cuenta las intenciones pendientes de revisar`() = runTest {
        parsedIntentRepository.save("n1", intent(needsConfirmation = true))
        parsedIntentRepository.save("n2", intent(needsConfirmation = false))
        parsedIntentRepository.save("n3", intent(needsConfirmation = true))

        val vm = viewModel()
        collectState(vm)

        assertEquals(2, vm.uiState.value.awaitingReview)
    }

    @Test
    fun `cuenta solo los items de checklist abiertos`() = runTest {
        checklistRepository.save(checklistItem("c1", done = false))
        checklistRepository.save(checklistItem("c2", done = false))
        checklistRepository.save(checklistItem("c3", done = true))

        val vm = viewModel()
        collectState(vm)

        assertEquals(2, vm.uiState.value.openActions)
    }

    @Test
    fun `el feed solo tiene notas de hoy, recientes primero, con tope de 4`() = runTest {
        voiceNoteRepository.save(note("ayer", "2026-07-01T10:00:00Z", "nota de ayer"))
        voiceNoteRepository.save(note("a", "2026-07-02T08:00:00Z", "primera"))
        voiceNoteRepository.save(note("b", "2026-07-02T09:00:00Z", "segunda"))
        voiceNoteRepository.save(note("c", "2026-07-02T10:00:00Z", "tercera"))
        voiceNoteRepository.save(note("d", "2026-07-02T11:00:00Z", "cuarta"))
        voiceNoteRepository.save(note("e", "2026-07-02T12:00:00Z", "quinta"))
        parsedIntentRepository.save("e", intent(needsConfirmation = false))

        val vm = viewModel()
        collectState(vm)

        val feed = vm.uiState.value.feed
        assertEquals(4, feed.size)
        assertEquals(listOf("quinta", "cuarta", "tercera", "segunda"), feed.map { it.text })
        // La nota con intención lleva su tipo; el resto, el estado de la nota.
        assertEquals(IntentType.SHOPPING.name, feed.first().tag)
        assertEquals(VoiceNoteStatus.TRANSCRIBED.name, feed.last().tag)
    }

    @Test
    fun `sin permiso de calendario la agenda aparece desconectada`() = runTest {
        val vm = viewModel()
        collectState(vm)

        val state = vm.uiState.value
        assertEquals(false, state.calendarConnected)
        assertNull(state.nextEventLabel)
    }

    @Test
    fun `con permiso muestra el proximo evento futuro de hoy`() = runTest {
        calendarRepository.permission = true
        calendarRepository.events = listOf(
            // now() del FixedTimeProvider = 10:15 UTC: el de las 09:00 ya pasó.
            event("pasado", "2026-07-02T09:00:00Z", "Desayuno"),
            event("proximo", "2026-07-02T11:00:00Z", "Reunión"),
            event("luego", "2026-07-02T15:00:00Z", "Dentista"),
        )
        val vm = viewModel()
        vm.refreshAgenda()
        collectState(vm)

        val state = vm.uiState.value
        assertEquals(true, state.calendarConnected)
        assertEquals("11:00 Reunión", state.nextEventLabel)
        assertEquals(3, state.eventsToday)
    }

    @Test
    fun `con permiso y sin eventos futuros la etiqueta queda vacia`() = runTest {
        calendarRepository.permission = true
        calendarRepository.events = listOf(event("pasado", "2026-07-02T09:00:00Z", "Desayuno"))
        val vm = viewModel()
        vm.refreshAgenda()
        collectState(vm)

        val state = vm.uiState.value
        assertEquals(true, state.calendarConnected)
        assertNull(state.nextEventLabel)
        assertEquals(1, state.eventsToday)
    }

    private fun event(id: String, at: String, title: String) = CalendarEvent(
        id = id,
        title = title,
        startAt = Instant.parse(at),
        endAt = Instant.parse(at).plusSeconds(3600),
        allDay = false,
    )

    private fun reminder(id: String, at: String, message: String) = Reminder(
        id = id,
        voiceNoteId = "n-$id",
        message = message,
        triggerAt = Instant.parse(at),
        status = ReminderStatus.PENDING,
    )

    private fun note(id: String, at: String, transcript: String) = VoiceNote(
        id = id,
        audioUri = null,
        transcript = transcript,
        createdAt = Instant.parse(at),
        status = VoiceNoteStatus.TRANSCRIBED,
    )

    private fun intent(needsConfirmation: Boolean) = ParsedIntent(
        intentType = IntentType.SHOPPING,
        confidence = 0.8f,
        title = "SHOPPING",
        summary = "",
        assistantResponse = "ok",
        needsConfirmation = needsConfirmation,
    )

    private fun checklistItem(id: String, done: Boolean) = ChecklistItem(
        id = id,
        voiceNoteId = "n1",
        type = ActionType.CARRY,
        label = "item $id",
        done = done,
        createdAt = Instant.parse("2026-07-02T09:00:00Z"),
    )
}
