package com.nehonar.operator.feature.capture

import com.nehonar.operator.core.ai.AIParseResult
import com.nehonar.operator.core.ai.ClarifyingQuestion
import com.nehonar.operator.core.ai.IntentType
import com.nehonar.operator.core.ai.ParsedIntent
import com.nehonar.operator.core.domain.model.VoiceNoteStatus
import com.nehonar.operator.core.voice.SttError
import com.nehonar.operator.core.voice.SttEvent
import com.nehonar.operator.testing.FakeAIProvider
import com.nehonar.operator.testing.FakeParsedIntentRepository
import com.nehonar.operator.testing.FakeSequentialSpeechToText
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
            AIParseResult.Success(
                ParsedIntent(
                    intentType = IntentType.SHOPPING,
                    confidence = 0.8f,
                    title = "SHOPPING",
                    summary = it,
                    assistantResponse = "Recado detectado: compra pendiente.",
                ),
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
    fun `pregunta de aclaracion pasa a AwaitingAnswer y guarda la nota parcial`() = runTest {
        val aiProvider = FakeAIProvider { transcript -> reminderMissingDepartureTime(transcript) }
        val vm = viewModel(
            listOf(SttEvent.Ready, SttEvent.FinalResult("mañana médico a las 8")),
            aiProvider = aiProvider,
        )

        vm.startCapture()

        val state = vm.uiState.value
        assertTrue(state is CaptureUiState.AwaitingAnswer)
        assertEquals(
            "Aún me falta un dato, señor: ¿A qué hora sale de casa?",
            (state as CaptureUiState.AwaitingAnswer).prompt,
        )

        val saved = voiceNoteRepository.current.single()
        assertEquals("mañana médico a las 8", saved.transcript)
        assertEquals(VoiceNoteStatus.TRANSCRIBED, saved.status)
        assertEquals(0, parsedIntentRepository.current.size)
    }

    @Test
    fun `responder la pregunta completa la conversacion en la misma nota`() = runTest {
        val aiProvider = FakeAIProvider { transcript ->
            if (transcript.contains("7:50")) {
                AIParseResult.Success(
                    ParsedIntent(
                        intentType = IntentType.REMINDER,
                        confidence = 0.8f,
                        title = "REMINDER",
                        summary = transcript,
                        assistantResponse = "Recordatorio programado, señor.",
                    ),
                )
            } else {
                reminderMissingDepartureTime(transcript)
            }
        }
        val speechToText = FakeSequentialSpeechToText(
            listOf(
                listOf(SttEvent.Ready, SttEvent.FinalResult("mañana médico a las 8")),
                listOf(SttEvent.Ready, SttEvent.FinalResult("quiero salir a las 7:50")),
            ),
        )
        val vm = CaptureViewModel(
            speechToText = speechToText,
            voiceNoteRepository = voiceNoteRepository,
            parsedIntentRepository = parsedIntentRepository,
            aiProvider = aiProvider,
            timeProvider = timeProvider,
        )

        vm.startCapture()
        assertTrue(vm.uiState.value is CaptureUiState.AwaitingAnswer)

        vm.startAnsweringClarification()

        val state = vm.uiState.value
        assertTrue(state is CaptureUiState.Parsed)
        val noteId = (state as CaptureUiState.Parsed).voiceNoteId
        val saved = voiceNoteRepository.current.single()
        assertEquals(noteId, saved.id)
        assertEquals("mañana médico a las 8. quiero salir a las 7:50", saved.transcript)
        assertEquals(VoiceNoteStatus.PARSED, saved.status)
        val savedIntent = parsedIntentRepository.current.getValue(noteId)
        assertEquals(IntentType.REMINDER, savedIntent.intentType)
        assertTrue(savedIntent.clarifyingQuestions.isEmpty())
    }

    @Test
    fun `tras 3 rondas sin respuesta valida se guarda igualmente en vez de bucle infinito`() = runTest {
        val aiProvider = FakeAIProvider { transcript -> reminderMissingDepartureTime(transcript) }
        val speechToText = FakeSequentialSpeechToText(
            listOf(
                listOf(SttEvent.Ready, SttEvent.FinalResult("recuérdame algo")),
                listOf(SttEvent.Ready, SttEvent.FinalResult("respuesta 1")),
                listOf(SttEvent.Ready, SttEvent.FinalResult("respuesta 2")),
                listOf(SttEvent.Ready, SttEvent.FinalResult("respuesta 3")),
            ),
        )
        val vm = CaptureViewModel(
            speechToText = speechToText,
            voiceNoteRepository = voiceNoteRepository,
            parsedIntentRepository = parsedIntentRepository,
            aiProvider = aiProvider,
            timeProvider = timeProvider,
        )

        vm.startCapture()
        assertTrue(vm.uiState.value is CaptureUiState.AwaitingAnswer)
        vm.startAnsweringClarification()
        assertTrue(vm.uiState.value is CaptureUiState.AwaitingAnswer)
        vm.startAnsweringClarification()
        assertTrue(vm.uiState.value is CaptureUiState.AwaitingAnswer)
        vm.startAnsweringClarification()

        val state = vm.uiState.value
        assertTrue(state is CaptureUiState.Parsed)
        val savedIntent = parsedIntentRepository.current.getValue((state as CaptureUiState.Parsed).voiceNoteId)
        assertEquals(1, savedIntent.clarifyingQuestions.size)
    }

    @Test
    fun `cancelar desde AwaitingAnswer vuelve a Idle sin borrar la nota parcial`() = runTest {
        val aiProvider = FakeAIProvider { transcript -> reminderMissingDepartureTime(transcript) }
        val vm = viewModel(
            listOf(SttEvent.Ready, SttEvent.FinalResult("recuérdame algo")),
            aiProvider = aiProvider,
        )

        vm.startCapture()
        assertTrue(vm.uiState.value is CaptureUiState.AwaitingAnswer)

        vm.cancelCapture()

        assertEquals(CaptureUiState.Idle, vm.uiState.value)
        val saved = voiceNoteRepository.current.single()
        assertEquals("recuérdame algo", saved.transcript)
        assertEquals(VoiceNoteStatus.TRANSCRIBED, saved.status)
    }

    private fun reminderMissingDepartureTime(transcript: String) = AIParseResult.Success(
        ParsedIntent(
            intentType = IntentType.REMINDER,
            confidence = 0.5f,
            title = "REMINDER",
            summary = transcript,
            clarifyingQuestions = listOf(ClarifyingQuestion("departure_time", "¿A qué hora sale de casa?")),
            assistantResponse = "Aún me falta un dato, señor: ¿A qué hora sale de casa?",
        ),
    )

    @Test
    fun `fallo de la IA guarda la nota como pendiente sin perderla`() = runTest {
        val aiProvider = FakeAIProvider { AIParseResult.Failure("Sin conexión") }
        val vm = viewModel(
            listOf(SttEvent.Ready, SttEvent.FinalResult("comprar fruta")),
            aiProvider = aiProvider,
        )

        vm.startCapture()

        val state = vm.uiState.value
        assertTrue(state is CaptureUiState.SavedPending)
        val pending = state as CaptureUiState.SavedPending
        assertEquals("Sin conexión", pending.reason)

        val saved = voiceNoteRepository.current.single()
        assertEquals(pending.voiceNoteId, saved.id)
        assertEquals(VoiceNoteStatus.TRANSCRIBED, saved.status)
        assertEquals(0, parsedIntentRepository.current.size)
    }

    @Test
    fun `onNavigatedToHistory vuelve a Idle solo si el estado era SavedPending`() = runTest {
        val aiProvider = FakeAIProvider { AIParseResult.Failure("Sin conexión") }
        val vm = viewModel(
            listOf(SttEvent.Ready, SttEvent.FinalResult("comprar fruta")),
            aiProvider = aiProvider,
        )
        vm.startCapture()
        assertTrue(vm.uiState.value is CaptureUiState.SavedPending)

        vm.onNavigatedToHistory()

        assertEquals(CaptureUiState.Idle, vm.uiState.value)
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
