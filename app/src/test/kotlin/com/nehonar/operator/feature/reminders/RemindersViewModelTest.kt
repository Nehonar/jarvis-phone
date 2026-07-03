package com.nehonar.operator.feature.reminders

import com.nehonar.operator.core.domain.model.Reminder
import com.nehonar.operator.core.domain.model.ReminderStatus
import com.nehonar.operator.testing.FakeReminderRepository
import com.nehonar.operator.testing.FakeReminderScheduler
import com.nehonar.operator.testing.MainDispatcherRule
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RemindersViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `lista solo los recordatorios pendientes ordenados por instante`() = runTest {
        val reminderRepository = FakeReminderRepository()
        reminderRepository.save(reminder("late", 2_000L))
        reminderRepository.save(reminder("early", 1_000L))
        reminderRepository.save(reminder("done", 500L, ReminderStatus.DONE))

        val vm = RemindersViewModel(reminderRepository, FakeReminderScheduler())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.items.collect {}
        }

        assertEquals(listOf("early", "late"), vm.items.value.map { it.id })
    }

    @Test
    fun `marcar como hecho cambia el estado y cancela la alarma`() = runTest {
        val reminderRepository = FakeReminderRepository()
        reminderRepository.save(reminder("r1", 1_000L))
        val reminderScheduler = FakeReminderScheduler()
        val vm = RemindersViewModel(reminderRepository, reminderScheduler)

        vm.markDone("r1")

        assertEquals(ReminderStatus.DONE, reminderRepository.current.getValue("r1").status)
        assertEquals(listOf("r1"), reminderScheduler.cancelled)
    }

    @Test
    fun `descartar cambia el estado y cancela la alarma`() = runTest {
        val reminderRepository = FakeReminderRepository()
        reminderRepository.save(reminder("r1", 1_000L))
        val reminderScheduler = FakeReminderScheduler()
        val vm = RemindersViewModel(reminderRepository, reminderScheduler)

        vm.dismiss("r1")

        assertEquals(ReminderStatus.DISMISSED, reminderRepository.current.getValue("r1").status)
        assertEquals(listOf("r1"), reminderScheduler.cancelled)
    }

    @Test
    fun `expone el estado del permiso de alarmas exactas y lo refresca`() = runTest {
        val reminderScheduler = FakeReminderScheduler().apply { exactAlarmsEnabled = false }
        val vm = RemindersViewModel(FakeReminderRepository(), reminderScheduler)

        assertEquals(false, vm.exactAlarmsEnabled.value)

        reminderScheduler.exactAlarmsEnabled = true
        vm.refreshPermissions()

        assertEquals(true, vm.exactAlarmsEnabled.value)
    }

    @Test
    fun `posponer suma 15 minutos y reprograma`() = runTest {
        val reminderRepository = FakeReminderRepository()
        reminderRepository.save(reminder("r1", 1_000L))
        val reminderScheduler = FakeReminderScheduler()
        val vm = RemindersViewModel(reminderRepository, reminderScheduler)

        vm.postpone("r1")

        val updated = reminderRepository.current.getValue("r1")
        assertEquals(Instant.ofEpochMilli(1_000L).plusSeconds(15 * 60), updated.triggerAt)
        assertEquals(ReminderStatus.PENDING, updated.status)
        assertTrue(reminderScheduler.scheduled.any { it.id == "r1" })
    }

    private fun reminder(
        id: String,
        epochMillis: Long,
        status: ReminderStatus = ReminderStatus.PENDING,
    ) = Reminder(
        id = id,
        voiceNoteId = "n1",
        message = "recordatorio $id",
        triggerAt = Instant.ofEpochMilli(epochMillis),
        status = status,
    )
}
