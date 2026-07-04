package com.nehonar.operator.feature.history

import com.nehonar.operator.core.ai.AIParseResult
import com.nehonar.operator.core.ai.IntentType
import com.nehonar.operator.core.ai.ParsedIntent
import com.nehonar.operator.core.domain.model.Reminder
import com.nehonar.operator.core.domain.model.ReminderStatus
import com.nehonar.operator.core.domain.model.VoiceNote
import com.nehonar.operator.core.domain.model.VoiceNoteStatus
import com.nehonar.operator.testing.FakeAIProvider
import com.nehonar.operator.testing.FakeChecklistRepository
import com.nehonar.operator.testing.FakeParsedIntentRepository
import com.nehonar.operator.testing.FakeReminderRepository
import com.nehonar.operator.testing.FakeReminderScheduler
import com.nehonar.operator.testing.FakeWidgetRefresher
import com.nehonar.operator.testing.FakeVoiceNoteRepository
import com.nehonar.operator.testing.MainDispatcherRule
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `lista las notas con su tipo de intencion y el borrado las elimina`() = runTest {
        val voiceNoteRepository = FakeVoiceNoteRepository()
        val parsedIntentRepository = FakeParsedIntentRepository()
        voiceNoteRepository.save(note("a", "comprar fruta", 1_000L))
        voiceNoteRepository.save(note("b", "llamar a Marc", 2_000L))
        parsedIntentRepository.save("b", sampleIntent(IntentType.CALL_OR_MESSAGE))

        val vm = HistoryViewModel(voiceNoteRepository, parsedIntentRepository, FakeAIProvider(), FakeReminderRepository(), FakeReminderScheduler(), FakeWidgetRefresher(), FakeChecklistRepository())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.items.collect {}
        }

        assertEquals(listOf("b", "a"), vm.items.value.map { it.id })
        assertEquals("llamar a Marc", vm.items.value.first().transcript)
        assertEquals(IntentType.CALL_OR_MESSAGE, vm.items.value.first().intentType)
        assertEquals(null, vm.items.value.last().intentType)

        vm.delete("b")

        assertEquals(listOf("a"), vm.items.value.map { it.id })
        assertEquals(null, parsedIntentRepository.getByVoiceNoteId("b"))
    }

    @Test
    fun `borrar una nota cancela y elimina sus recordatorios en cascada`() = runTest {
        val voiceNoteRepository = FakeVoiceNoteRepository()
        val parsedIntentRepository = FakeParsedIntentRepository()
        val reminderRepository = FakeReminderRepository()
        val reminderScheduler = FakeReminderScheduler()
        val widgetRefresher = FakeWidgetRefresher()
        voiceNoteRepository.save(note("a", "recuerdame el medico", 1_000L))
        reminderRepository.save(
            Reminder(
                id = "r1",
                voiceNoteId = "a",
                message = "ir al medico",
                triggerAt = Instant.ofEpochMilli(9_000L),
                status = ReminderStatus.PENDING,
            ),
        )

        val vm = HistoryViewModel(voiceNoteRepository, parsedIntentRepository, FakeAIProvider(), reminderRepository, reminderScheduler, widgetRefresher, FakeChecklistRepository())

        vm.delete("a")

        assertEquals(null, voiceNoteRepository.getById("a"))
        assertTrue(reminderRepository.current.isEmpty())
        assertEquals(listOf("r1"), reminderScheduler.cancelled)
        assertEquals(1, widgetRefresher.refreshCount)
    }

    @Test
    fun `solo las notas TRANSCRIBED permiten reintentar`() = runTest {
        val voiceNoteRepository = FakeVoiceNoteRepository()
        val parsedIntentRepository = FakeParsedIntentRepository()
        voiceNoteRepository.save(note("pending", "comprar fruta", 1_000L, VoiceNoteStatus.TRANSCRIBED))
        voiceNoteRepository.save(note("done", "llamar a Marc", 2_000L, VoiceNoteStatus.PARSED))

        val vm = HistoryViewModel(voiceNoteRepository, parsedIntentRepository, FakeAIProvider(), FakeReminderRepository(), FakeReminderScheduler(), FakeWidgetRefresher(), FakeChecklistRepository())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.items.collect {}
        }

        val byId = vm.items.value.associateBy { it.id }
        assertTrue(byId.getValue("pending").canRetry)
        assertEquals(false, byId.getValue("done").canRetry)
    }

    @Test
    fun `reintentar con exito pasa la nota a PARSED y pide navegar a review`() = runTest {
        val voiceNoteRepository = FakeVoiceNoteRepository()
        val parsedIntentRepository = FakeParsedIntentRepository()
        voiceNoteRepository.save(note("pending", "comprar fruta", 1_000L, VoiceNoteStatus.TRANSCRIBED))
        val aiProvider = FakeAIProvider {
            AIParseResult.Success(sampleIntent(IntentType.SHOPPING))
        }
        val vm = HistoryViewModel(voiceNoteRepository, parsedIntentRepository, aiProvider, FakeReminderRepository(), FakeReminderScheduler(), FakeWidgetRefresher(), FakeChecklistRepository())

        vm.retryParsing("pending")

        assertEquals(VoiceNoteStatus.PARSED, voiceNoteRepository.getById("pending")?.status)
        assertEquals(IntentType.SHOPPING, parsedIntentRepository.getByVoiceNoteId("pending")?.intentType)
        assertEquals("pending", vm.navigateToReviewId.value)

        vm.consumeNavigation()
        assertNull(vm.navigateToReviewId.value)
    }

    @Test
    fun `reintentar con fallo deja la nota pendiente y no navega`() = runTest {
        val voiceNoteRepository = FakeVoiceNoteRepository()
        val parsedIntentRepository = FakeParsedIntentRepository()
        voiceNoteRepository.save(note("pending", "comprar fruta", 1_000L, VoiceNoteStatus.TRANSCRIBED))
        val aiProvider = FakeAIProvider { AIParseResult.Failure("Sin conexión") }
        val vm = HistoryViewModel(voiceNoteRepository, parsedIntentRepository, aiProvider, FakeReminderRepository(), FakeReminderScheduler(), FakeWidgetRefresher(), FakeChecklistRepository())

        vm.retryParsing("pending")

        assertEquals(VoiceNoteStatus.TRANSCRIBED, voiceNoteRepository.getById("pending")?.status)
        assertNull(parsedIntentRepository.getByVoiceNoteId("pending"))
        assertNull(vm.navigateToReviewId.value)
    }

    private fun note(
        id: String,
        transcript: String,
        epochMillis: Long,
        status: VoiceNoteStatus = VoiceNoteStatus.TRANSCRIBED,
    ) = VoiceNote(
        id = id,
        audioUri = null,
        transcript = transcript,
        createdAt = Instant.ofEpochMilli(epochMillis),
        status = status,
    )

    private fun sampleIntent(type: IntentType) = ParsedIntent(
        intentType = type,
        confidence = 0.8f,
        title = type.name,
        summary = "",
        assistantResponse = "ok",
    )
}
