package com.nehonar.operator.core.widget

import com.nehonar.operator.core.domain.model.Reminder
import com.nehonar.operator.core.domain.model.ReminderStatus
import com.nehonar.operator.testing.FakeReminderRepository
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetStateLoaderTest {

    private val reminderRepository = FakeReminderRepository()
    private val loader = WidgetStateLoader(reminderRepository)

    @Test
    fun `sin pendientes devuelve estado vacio`() = runTest {
        val state = loader.load(ZoneOffset.UTC)

        assertNull(state.nextTimeLabel)
        assertNull(state.nextMessage)
        assertEquals(0, state.pendingCount)
    }

    @Test
    fun `con pendientes devuelve el mas proximo y el total`() = runTest {
        reminderRepository.save(reminder("late", "2026-07-02T15:00:00Z", "Recoger paquete"))
        reminderRepository.save(reminder("soon", "2026-07-02T11:00:00Z", "Salir de casa"))
        reminderRepository.save(
            reminder("done", "2026-07-02T09:00:00Z", "Ya hecho", ReminderStatus.DONE),
        )

        val state = loader.load(ZoneOffset.UTC)

        assertEquals("JUE 2026-07-02 11:00", state.nextTimeLabel)
        assertEquals("Salir de casa", state.nextMessage)
        assertEquals(2, state.pendingCount)
    }

    private fun reminder(
        id: String,
        at: String,
        message: String,
        status: ReminderStatus = ReminderStatus.PENDING,
    ) = Reminder(
        id = id,
        voiceNoteId = "n-$id",
        message = message,
        triggerAt = Instant.parse(at),
        status = status,
    )
}
