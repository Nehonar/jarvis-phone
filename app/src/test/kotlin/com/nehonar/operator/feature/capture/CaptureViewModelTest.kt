package com.nehonar.operator.feature.capture

import com.nehonar.operator.core.domain.model.VoiceNoteStatus
import com.nehonar.operator.core.voice.SttError
import com.nehonar.operator.core.voice.SttEvent
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

    private val repository = FakeVoiceNoteRepository()
    private val timeProvider = FixedTimeProvider()

    private fun viewModel(events: List<SttEvent>, available: Boolean = true) =
        CaptureViewModel(
            speechToText = FakeSpeechToText(events, available),
            repository = repository,
            timeProvider = timeProvider,
        )

    @Test
    fun `camino feliz guarda la nota y termina en Done`() = runTest {
        val vm = viewModel(
            listOf(
                SttEvent.Ready,
                SttEvent.SpeechStart,
                SttEvent.Level(4f),
                SttEvent.Partial("mañana"),
                SttEvent.Partial("mañana oficina"),
                SttEvent.SpeechEnd,
                SttEvent.FinalResult("mañana oficina, llevar portátil"),
            ),
        )

        vm.startCapture()

        val state = vm.uiState.value
        assertTrue(state is CaptureUiState.Done)
        assertEquals("mañana oficina, llevar portátil", (state as CaptureUiState.Done).transcript)
        assertEquals(1, repository.current.size)
        val saved = repository.current.single()
        assertEquals("mañana oficina, llevar portátil", saved.transcript)
        assertEquals(VoiceNoteStatus.TRANSCRIBED, saved.status)
        assertEquals(timeProvider.now(), saved.createdAt)
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
        assertEquals(0, repository.current.size)
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
        assertEquals(0, repository.current.size)
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
    fun `descartar borra la nota recien guardada y vuelve a Idle`() = runTest {
        val vm = viewModel(
            listOf(SttEvent.Ready, SttEvent.FinalResult("comprar fruta")),
        )
        vm.startCapture()
        assertEquals(1, repository.current.size)

        vm.discardNote()

        assertEquals(0, repository.current.size)
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
