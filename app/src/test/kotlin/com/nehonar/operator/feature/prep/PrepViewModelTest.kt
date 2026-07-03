package com.nehonar.operator.feature.prep

import com.nehonar.operator.core.ai.ActionType
import com.nehonar.operator.core.domain.model.ChecklistItem
import com.nehonar.operator.testing.FakeChecklistRepository
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
class PrepViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `agrupa por tipo y cuenta abiertos y hechos`() = runTest {
        val repository = FakeChecklistRepository()
        repository.save(item("c1", ActionType.CARRY, "el portátil", at = 1_000L))
        repository.save(item("c2", ActionType.CARRY, "el cargador", at = 2_000L))
        repository.save(item("c3", ActionType.BUY, "fruta", done = true, at = 3_000L))

        val vm = PrepViewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect {}
        }

        val state = vm.uiState.value
        assertEquals(2, state.groups.size)
        assertEquals(2, state.openCount)
        assertEquals(1, state.doneCount)
        val carryGroup = state.groups.first { it.type == ActionType.CARRY }
        assertEquals(listOf("el portátil", "el cargador"), carryGroup.items.map { it.label })
    }

    @Test
    fun `toggle alterna hecho y persiste`() = runTest {
        val repository = FakeChecklistRepository()
        repository.save(item("c1", ActionType.CARRY, "el portátil"))
        val vm = PrepViewModel(repository)

        vm.toggle("c1")
        assertEquals(true, repository.current.getValue("c1").done)

        vm.toggle("c1")
        assertEquals(false, repository.current.getValue("c1").done)
    }

    @Test
    fun `borrar elimina el item`() = runTest {
        val repository = FakeChecklistRepository()
        repository.save(item("c1", ActionType.BUY, "fruta"))
        val vm = PrepViewModel(repository)

        vm.delete("c1")

        assertNull(repository.current["c1"])
    }

    @Test
    fun `limpiar hechos elimina solo los completados`() = runTest {
        val repository = FakeChecklistRepository()
        repository.save(item("open", ActionType.CARRY, "el portátil"))
        repository.save(item("done", ActionType.BUY, "fruta", done = true))
        val vm = PrepViewModel(repository)

        vm.clearDone()

        assertTrue(repository.current.containsKey("open"))
        assertNull(repository.current["done"])
    }

    private fun item(
        id: String,
        type: ActionType,
        label: String,
        done: Boolean = false,
        at: Long = 1_000L,
    ) = ChecklistItem(
        id = id,
        voiceNoteId = "n1",
        type = type,
        label = label,
        done = done,
        createdAt = Instant.ofEpochMilli(at),
    )
}
