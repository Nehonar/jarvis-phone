package com.nehonar.operator.feature.review

import androidx.lifecycle.SavedStateHandle
import com.nehonar.operator.core.ai.AIParseResult
import com.nehonar.operator.core.ai.IntentType
import com.nehonar.operator.core.ai.ParsedIntent
import com.nehonar.operator.core.domain.model.VoiceNote
import com.nehonar.operator.core.domain.model.VoiceNoteStatus
import com.nehonar.operator.testing.FakeAIProvider
import com.nehonar.operator.testing.FakeParsedIntentRepository
import com.nehonar.operator.testing.FakeVoiceNoteRepository
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
    fun `descartar borra nota e intencion y pasa a Done`() = runTest {
        seedNote("n1", "comprar fruta", sampleIntent())
        val vm = viewModel("n1")

        vm.discard()

        assertEquals(ReviewUiState.Done, vm.uiState.value)
        assertEquals(null, voiceNoteRepository.getById("n1"))
        assertEquals(null, parsedIntentRepository.getByVoiceNoteId("n1"))
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
