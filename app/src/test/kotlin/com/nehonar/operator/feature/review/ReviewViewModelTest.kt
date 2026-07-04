package com.nehonar.operator.feature.review

import androidx.lifecycle.SavedStateHandle
import com.nehonar.operator.core.ai.AIParseResult
import com.nehonar.operator.core.ai.ActionItem
import com.nehonar.operator.core.ai.ActionType
import com.nehonar.operator.core.ai.IntentType
import com.nehonar.operator.core.ai.MemoryFactDraft
import com.nehonar.operator.core.ai.ParsedIntent
import com.nehonar.operator.core.ai.Priority
import com.nehonar.operator.core.domain.model.VoiceNote
import com.nehonar.operator.core.domain.model.VoiceNoteStatus
import com.nehonar.operator.testing.FakeAIProvider
import com.nehonar.operator.testing.FakeChecklistRepository
import com.nehonar.operator.testing.FakeMemoryRepository
import com.nehonar.operator.testing.FakeParsedIntentRepository
import com.nehonar.operator.testing.FakeReminderRepository
import com.nehonar.operator.testing.FakeReminderScheduler
import com.nehonar.operator.testing.FakeVoiceNoteRepository
import com.nehonar.operator.testing.FakeWidgetRefresher
import com.nehonar.operator.testing.FixedTimeProvider
import com.nehonar.operator.testing.MainDispatcherRule
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ReviewViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val voiceNoteRepository = FakeVoiceNoteRepository()
    private val parsedIntentRepository = FakeParsedIntentRepository()
    private val reminderRepository = FakeReminderRepository()
    private val reminderScheduler = FakeReminderScheduler()
    private val timeProvider = FixedTimeProvider()
    private val widgetRefresher = FakeWidgetRefresher()
    private val checklistRepository = FakeChecklistRepository()
    private val memoryRepository = FakeMemoryRepository()

    private suspend fun seedNote(id: String, transcript: String, intent: ParsedIntent) {
        voiceNoteRepository.save(
            VoiceNote(
                id = id,
                audioUri = null,
                transcript = transcript,
                createdAt = FixedTimeProvider().now(),
                status = VoiceNoteStatus.PARSED,
            ),
        )
        parsedIntentRepository.save(id, intent)
    }

    private fun viewModel(voiceNoteId: String, aiProvider: FakeAIProvider = FakeAIProvider()) =
        ReviewViewModel(
            savedStateHandle = SavedStateHandle(mapOf("voiceNoteId" to voiceNoteId)),
            voiceNoteRepository = voiceNoteRepository,
            parsedIntentRepository = parsedIntentRepository,
            aiProvider = aiProvider,
            reminderRepository = reminderRepository,
            reminderScheduler = reminderScheduler,
            timeProvider = timeProvider,
            widgetRefresher = widgetRefresher,
            checklistRepository = checklistRepository,
            memoryRepository = memoryRepository,
        )

    private fun sampleIntent() = ParsedIntent(
        intentType = IntentType.SHOPPING,
        confidence = 0.8f,
        title = "SHOPPING",
        summary = "comprar fruta",
        assistantResponse = "Recado detectado: compra pendiente.",
    )

    @Test
    fun `carga la nota y la intencion existentes`() = runTest {
        seedNote("n1", "comprar fruta", sampleIntent())

        val vm = viewModel("n1")

        val state = vm.uiState.value
        assertTrue(state is ReviewUiState.Content)
        assertEquals("comprar fruta", (state as ReviewUiState.Content).transcript)
        assertEquals(IntentType.SHOPPING, state.intent.intentType)
    }

    @Test
    fun `nota inexistente termina en NotFound`() = runTest {
        val vm = viewModel("no-existe")

        assertEquals(ReviewUiState.NotFound, vm.uiState.value)
    }

    @Test
    fun `aceptar marca la intencion como confirmada y pasa a Done`() = runTest {
        seedNote("n1", "comprar fruta", sampleIntent())
        val vm = viewModel("n1")

        vm.accept()

        assertEquals(ReviewUiState.Done, vm.uiState.value)
        assertEquals(false, parsedIntentRepository.current.getValue("n1").needsConfirmation)
    }

    @Test
    fun `aceptar con fecha y hora futuras crea y programa un recordatorio`() = runTest {
        val intent = sampleIntent().copy(
            intentType = IntentType.REMINDER,
            date = "2030-01-01",
            time = "09:00",
        )
        seedNote("n1", "recuerdame algo", intent)
        val vm = viewModel("n1")

        vm.accept()

        assertEquals(1, reminderRepository.current.size)
        val reminder = reminderRepository.current.values.single()
        assertEquals("n1", reminder.voiceNoteId)
        assertEquals(1, reminderScheduler.scheduled.size)
        assertEquals(reminder.id, reminderScheduler.scheduled.single().id)
        assertEquals(1, widgetRefresher.refreshCount)
    }

    @Test
    fun `aceptar persiste las acciones como items de checklist`() = runTest {
        val intent = sampleIntent().copy(
            intentType = IntentType.CARRY_ITEMS,
            actions = listOf(
                ActionItem(ActionType.CARRY, "el portátil", Priority.HIGH),
                ActionItem(ActionType.BUY, "fruta", Priority.MEDIUM),
            ),
        )
        seedNote("n1", "llevar el portátil y comprar fruta", intent)
        val vm = viewModel("n1")

        vm.accept()

        val items = checklistRepository.current.values.sortedBy { it.label }
        assertEquals(2, items.size)
        assertEquals(listOf("el portátil", "fruta"), items.map { it.label })
        assertEquals(listOf(ActionType.CARRY, ActionType.BUY), items.map { it.type })
        assertTrue(items.none { it.done })
        assertEquals("n1", items.first().voiceNoteId)
    }

    @Test
    fun `aceptar persiste los hechos memorables`() = runTest {
        val intent = sampleIntent().copy(
            memoryFacts = listOf(
                MemoryFactDraft(topic = "talla de pie", fact = "El usuario calza un 42"),
            ),
        )
        seedNote("n1", "apunta que mi talla de pie es el 42", intent)
        val vm = viewModel("n1")

        vm.accept()

        val fact = memoryRepository.current.values.single()
        assertEquals("talla de pie", fact.topic)
        assertEquals("El usuario calza un 42", fact.fact)
    }

    @Test
    fun `aceptar sin hechos no toca la memoria`() = runTest {
        seedNote("n1", "comprar fruta", sampleIntent())
        val vm = viewModel("n1")

        vm.accept()

        assertTrue(memoryRepository.current.isEmpty())
    }

    @Test
    fun `aceptar sin acciones no crea items de checklist`() = runTest {
        seedNote("n1", "comprar fruta", sampleIntent())
        val vm = viewModel("n1")

        vm.accept()

        assertTrue(checklistRepository.current.isEmpty())
    }

    @Test
    fun `aceptar con fecha y hora en el pasado no programa recordatorio`() = runTest {
        val intent = sampleIntent().copy(
            intentType = IntentType.REMINDER,
            date = "2020-01-01",
            time = "08:00",
        )
        seedNote("n1", "recuerdame algo", intent)
        val vm = viewModel("n1")

        vm.accept()

        assertTrue(reminderRepository.current.isEmpty())
        assertTrue(reminderScheduler.scheduled.isEmpty())
    }

    @Test
    fun `aceptar sin fecha u hora resueltas no programa recordatorio`() = runTest {
        seedNote("n1", "comprar fruta", sampleIntent())
        val vm = viewModel("n1")

        vm.accept()

        assertTrue(reminderRepository.current.isEmpty())
        assertTrue(reminderScheduler.scheduled.isEmpty())
    }

    @Test
    fun `descartar borra nota e intencion y pasa a Done`() = runTest {
        seedNote("n1", "comprar fruta", sampleIntent())
        val vm = viewModel("n1")

        vm.discard()

        assertEquals(ReviewUiState.Done, vm.uiState.value)
        assertEquals(null, voiceNoteRepository.getById("n1"))
        assertEquals(null, parsedIntentRepository.getByVoiceNoteId("n1"))
    }

    @Test
    fun `descartar cancela recordatorios, limpia checklist y refresca el widget`() = runTest {
        val intent = sampleIntent().copy(
            intentType = IntentType.REMINDER,
            date = "2030-01-01",
            time = "09:00",
            actions = listOf(ActionItem(ActionType.CARRY, "el portátil", Priority.HIGH)),
        )
        seedNote("n1", "recuerdame algo", intent)
        val vm = viewModel("n1")
        // Aceptar primero: crea recordatorio programado e item de checklist.
        vm.accept()
        assertEquals(1, reminderRepository.current.size)
        assertEquals(1, checklistRepository.current.size)
        val reminderId = reminderRepository.current.values.single().id

        val vm2 = viewModel("n1")
        vm2.discard()

        assertTrue(reminderRepository.current.isEmpty())
        assertTrue(checklistRepository.current.isEmpty())
        assertTrue(reminderScheduler.cancelled.contains(reminderId))
        assertEquals(null, voiceNoteRepository.getById("n1"))
    }

    @Test
    fun `editar texto reparsea con la IA y actualiza transcript e intencion`() = runTest {
        seedNote("n1", "comprar fruta", sampleIntent())
        val reparsed = ParsedIntent(
            intentType = IntentType.CARRY_ITEMS,
            confidence = 0.8f,
            title = "CARRY ITEMS",
            summary = "llevar paraguas",
            assistantResponse = "Checklist creada: paraguas.",
        )
        val aiProvider = FakeAIProvider { AIParseResult.Success(reparsed) }
        val vm = viewModel("n1", aiProvider)

        vm.startEditing()
        vm.saveEditedText("llevar paraguas")

        val state = vm.uiState.value
        assertTrue(state is ReviewUiState.Content)
        assertEquals("llevar paraguas", (state as ReviewUiState.Content).transcript)
        assertEquals(IntentType.CARRY_ITEMS, state.intent.intentType)
        assertEquals("llevar paraguas", voiceNoteRepository.getById("n1")?.transcript)
        assertEquals(VoiceNoteStatus.PARSED, voiceNoteRepository.getById("n1")?.status)
    }

    @Test
    fun `fallo de la IA al reparsear conserva el texto original y muestra el error`() = runTest {
        seedNote("n1", "comprar fruta", sampleIntent())
        val aiProvider = FakeAIProvider { AIParseResult.Failure("Sin conexión") }
        val vm = viewModel("n1", aiProvider)

        vm.startEditing()
        vm.saveEditedText("llevar paraguas")

        val state = vm.uiState.value
        assertTrue(state is ReviewUiState.Content)
        val content = state as ReviewUiState.Content
        assertTrue(content.isEditing)
        assertEquals("Sin conexión", content.editError)
        // El transcript guardado no cambia: el fallo no debe perder la versión aceptada.
        assertEquals("comprar fruta", voiceNoteRepository.getById("n1")?.transcript)
        assertEquals(IntentType.SHOPPING, parsedIntentRepository.getByVoiceNoteId("n1")?.intentType)
    }

    @Test
    fun `cancelar edicion vuelve al contenido sin editar`() = runTest {
        seedNote("n1", "comprar fruta", sampleIntent())
        val vm = viewModel("n1")

        vm.startEditing()
        vm.cancelEditing()

        val state = vm.uiState.value
        assertTrue(state is ReviewUiState.Content)
        assertEquals(false, (state as ReviewUiState.Content).isEditing)
    }
}
