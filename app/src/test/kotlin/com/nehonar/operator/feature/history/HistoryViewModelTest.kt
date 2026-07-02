package com.nehonar.operator.feature.history

import com.nehonar.operator.core.domain.model.VoiceNote
import com.nehonar.operator.core.domain.model.VoiceNoteStatus
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
    fun `lista las notas y el borrado las elimina`() = runTest {
        val repository = FakeVoiceNoteRepository()
        repository.save(note("a", "comprar fruta", 1_000L))
        repository.save(note("b", "llamar a Marc", 2_000L))

        val vm = HistoryViewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.items.collect {}
        }

        assertEquals(listOf("b", "a"), vm.items.value.map { it.id })
        assertEquals("llamar a Marc", vm.items.value.first().transcript)

        vm.delete("b")

        assertEquals(listOf("a"), vm.items.value.map { it.id })
    }

    private fun note(id: String, transcript: String, epochMillis: Long) = VoiceNote(
        id = id,
        audioUri = null,
        transcript = transcript,
        createdAt = Instant.ofEpochMilli(epochMillis),
        status = VoiceNoteStatus.TRANSCRIBED,
    )
}
