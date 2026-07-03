package com.nehonar.operator.feature.capture

import com.nehonar.operator.core.ai.IntentType
import com.nehonar.operator.core.ai.ParsedIntent
import com.nehonar.operator.core.domain.model.VoiceNoteStatus
import com.nehonar.operator.core.voice.SttError
import com.nehonar.operator.core.voice.SttEvent
import com.nehonar.operator.testing.FakeAIProvider
import com.nehonar.operator.testing.FakeParsedIntentRepository
import com.nehonar.operator.testing.FakeSpeechToText
import com.nehonar.operator.testing.FakeVoiceNoteRepository
import com.nehonar.operator.testing.FixedTimeProvider
import com.nehonar.operator.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CaptureViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val voiceNoteRepository = FakeVoiceNoteRepository()
    private val parsedIntentRepository = FakeParsedIntentRepository()
    private val timeProvider = FixedTimeProvider()

    private fun viewModel(
        events: List<SttEvent>,
        available: Boolean = true,
        aiProvider: FakeAIProvider = FakeAIProvider(),
    ) = CaptureViewModel(
        speechToText = FakeSpeechToText(events, available),
        voiceNoteRepository = voiceNoteRepository,
        parsedIntentRepository = parsedIntentRepository,
        aiProvider = aiProvider,
        timeProvider = timeProvider,
    )

    @Test
    fun `camino feliz guarda la nota, la parsea y termina en Parsed`() = runTest {
        val aiProvider = FakeAIProvider {
            ParsedIntent(
                intentType = IntentType.SHOPPING,
                confidence = 0.8f,
                title = "SHOPPING",
                summary = it,
                assistantResponse = "Recado detectado: compra pendiente.",
            )
        }
        val vm = viewModel(
            listOf(
                SttEvent.Ready,
                SttEvent.SpeechStart,
                SttEvent.Level(4f),
                SttEvent.Partial("comprar"),
                SttEvent.SpeechEnd,
                SttEvent.FinalResult("comprar fruta y yogures"),
            ),
            aiProvider = aiProvider,
        )

        vm.startCapture()

        val state = vm.uiState.value
        assertTrue(state is CaptureUiState.Parsed)
        val noteId = (state as CaptureUiState.Parsed).voiceNoteId

        assertEquals("comprar fruta y yogures", aiProvider.lastTranscript)
        val saved = voiceNoteRepository.current.single()
        assertEquals(noteId, saved.id)
        assertEquals(VoiceNoteStatus.PARSED, saved.status)
        assertEquals(timeProvider.now(), saved.createdAt)

        val savedIntent = parsedIntentRepository.current.getValue(noteId)
        assertEquals(IntentType.SHOPPING, savedIntent.intentType)
    }

    @Test
    fun `error del reconocedor no guarda nota y permite reintentar`() = runTest {
        val vm = viewModel(
            listOf(
                SttEvent.Ready,
                SttEvent.Failed(SttError.NO_SPEECH),
            ),
        )

        vm.startCapture()

        val state = vm.uiState.value
        assertTrue(state is CaptureUiState.Error)
        assertTrue((state as CaptureUiState.Error).canRetry)
        assertEquals(0, voiceNoteRepository.current.size)
        assertEquals(0, parsedIntentRepository.current.size)
    }

    @Test
    fun `resultado final vacio es error y no guarda nota vacia`() = runTest {
        val vm = viewModel(
            listOf(
                SttEvent.Ready,
                SttEvent.SpeechEnd,
                SttEvent.FinalResult("   "),
            ),
        )

        vm.startCapture()

        assertTrue(vm.uiState.value is CaptureUiState.Error)
        assertEquals(0, voiceNoteRepository.current.size)
    }

    @Test
    fun `stt no disponible es error sin reintento`() = runTest {
        val vm = viewModel(events = emptyList(), available = false)

        vm.startCapture()

        val state = vm.uiState.value
        assertTrue(state is CaptureUiState.Error)
        assertFalse((state as CaptureUiState.Error).canRetry)
    }

    @Test
    fun `onNavigatedToReview vuelve a Idle solo si el estado era Parsed`() = runTest {
        val vm = viewModel(
            listOf(SttEvent.Ready, SttEvent.FinalResult("comprar fruta")),
        )
        vm.startCapture()
        assertTrue(vm.uiState.value is CaptureUiState.Parsed)

        vm.onNavigatedToReview()

        assertEquals(CaptureUiState.Idle, vm.uiState.value)
    }

    @Test
    fun `permiso denegado es error con reintento`() = runTest {
        val vm = viewModel(events = emptyList())

        vm.onPermissionDenied()

        val state = vm.uiState.value
        assertTrue(state is CaptureUiState.Error)
        assertTrue((state as CaptureUiState.Error).canRetry)
    }
}
