package com.nehonar.operator.feature.history

import com.nehonar.operator.core.ai.IntentType
import com.nehonar.operator.core.ai.ParsedIntent
import com.nehonar.operator.core.domain.model.VoiceNote
import com.nehonar.operator.core.domain.model.VoiceNoteStatus
import com.nehonar.operator.testing.FakeParsedIntentRepository
import com.nehonar.operator.testing.FakeVoiceNoteRepository
import com.nehonar.operator.testing.MainDispatcherRule
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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

        val vm = HistoryViewModel(voiceNoteRepository, parsedIntentRepository)
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

    private fun note(id: String, transcript: String, epochMillis: Long) = VoiceNote(
        id = id,
        audioUri = null,
        transcript = transcript,
        createdAt = Instant.ofEpochMilli(epochMillis),
        status = VoiceNoteStatus.TRANSCRIBED,
    )

    private fun sampleIntent(type: IntentType) = ParsedIntent(
        intentType = type,
        confidence = 0.8f,
        title = type.name,
        summary = "",
        assistantResponse = "ok",
    )
}
