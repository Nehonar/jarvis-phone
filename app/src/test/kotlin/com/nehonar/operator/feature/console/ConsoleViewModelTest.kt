package com.nehonar.operator.feature.console

import com.nehonar.operator.core.ai.ActionType
import com.nehonar.operator.core.domain.model.ChecklistItem
import com.nehonar.operator.core.domain.model.Reminder
import com.nehonar.operator.core.domain.model.ReminderStatus
import com.nehonar.operator.core.domain.model.VoiceNote
import com.nehonar.operator.core.domain.model.VoiceNoteStatus
import com.nehonar.operator.testing.FakeChecklistRepository
import com.nehonar.operator.testing.FakeReminderRepository
import com.nehonar.operator.testing.FakeSpeaker
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConsoleViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // FixedTimeProvider: hoy = 2026-07-02 (UTC); instantes a media mañana.
    private val timeProvider = FixedTimeProvider()
    private val voiceNoteRepository = FakeVoiceNoteRepository()
    private val reminderRepository = FakeReminderRepository()
    private val checklistRepository = FakeChecklistRepository()
    private val speaker = FakeSpeaker()

    private fun viewModel() = ConsoleViewModel(
        timeProvider = timeProvider,
        voiceNoteRepository = voiceNoteRepository,
        reminderRepository = reminderRepository,
        checklistRepository = checklistRepository,
        speaker = speaker,
    )

    private fun TestScope.collectState(vm: ConsoleViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect {}
        }
    }

    @Test
    fun `sin datos queda en STANDBY sin nodos`() = runTest {
        val vm = viewModel()
        collectState(vm)

        val state = vm.uiState.value
        assertTrue(state.nodes.isEmpty())
        assertFalse(state.isActive)
        assertEquals(0f, state.activityLevel)
    }

    @Test
    fun `isSpeaking refleja el estado del operador`() = runTest {
        val vm = viewModel()

        assertFalse(vm.isSpeaking.value)
        speaker.isSpeaking.value = true
        assertTrue(vm.isSpeaking.value)
    }

    @Test
    fun `crea un nodo por dato con su tipo`() = runTest {
        reminderRepository.save(reminder("r1"))
        checklistRepository.save(checklistItem("c1", done = false))
        checklistRepository.save(checklistItem("c2", done = true))
        voiceNoteRepository.save(note("hoy", "2026-07-02T09:00:00Z"))
        voiceNoteRepository.save(note("ayer", "2026-07-01T09:00:00Z"))

        val vm = viewModel()
        collectState(vm)

        val state = vm.uiState.value
        assertEquals(1, state.nodes.count { it.kind == NodeKind.REMINDER })
        // Solo los abiertos generan nodo; los hechos no.
        assertEquals(1, state.nodes.count { it.kind == NodeKind.ACTION })
        // Solo las notas de hoy generan nodo.
        assertEquals(1, state.nodes.count { it.kind == NodeKind.NOTE })
        assertTrue(state.isActive)
        assertEquals(1, state.pendingReminders)
        assertEquals(1, state.openActions)
        assertEquals(1, state.notesToday)
    }

    @Test
    fun `los nodos se capan pero los contadores muestran el total real`() = runTest {
        repeat(10) { reminderRepository.save(reminder("r$it")) }

        val vm = viewModel()
        collectState(vm)

        val state = vm.uiState.value
        assertEquals(ConsoleViewModel.MAX_REMINDER_NODES, state.nodes.size)
        assertEquals(10, state.pendingReminders)
        assertEquals(1f, state.activityLevel)
    }

    private fun reminder(id: String) = Reminder(
        id = id,
        voiceNoteId = "n-$id",
        message = "recordatorio $id",
        triggerAt = Instant.parse("2026-07-02T11:00:00Z"),
        status = ReminderStatus.PENDING,
    )

    private fun checklistItem(id: String, done: Boolean) = ChecklistItem(
        id = id,
        voiceNoteId = "n1",
        type = ActionType.CARRY,
        label = "item $id",
        done = done,
        createdAt = Instant.parse("2026-07-02T09:00:00Z"),
    )

    private fun note(id: String, at: String) = VoiceNote(
        id = id,
        audioUri = null,
        transcript = "nota $id",
        createdAt = Instant.parse(at),
        status = VoiceNoteStatus.TRANSCRIBED,
    )
}
