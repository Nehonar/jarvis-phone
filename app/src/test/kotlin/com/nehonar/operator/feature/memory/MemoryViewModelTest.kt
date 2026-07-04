package com.nehonar.operator.feature.memory

import com.nehonar.operator.core.domain.model.MemoryFact
import com.nehonar.operator.testing.FakeMemoryRepository
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
class MemoryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `lista los hechos mas recientes primero y el borrado los elimina`() = runTest {
        val repository = FakeMemoryRepository()
        repository.save(fact("viejo", 1_000L, "El usuario calza un 42"))
        repository.save(fact("nuevo", 2_000L, "A Marc le gusta el vino tinto"))

        val vm = MemoryViewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.items.collect {}
        }

        assertEquals(listOf("nuevo", "viejo"), vm.items.value.map { it.id })
        assertEquals("A Marc le gusta el vino tinto", vm.items.value.first().fact)

        vm.delete("nuevo")

        assertEquals(listOf("viejo"), vm.items.value.map { it.id })
    }

    private fun fact(id: String, epochMillis: Long, text: String) = MemoryFact(
        id = id,
        topic = "tema $id",
        fact = text,
        createdAt = Instant.ofEpochMilli(epochMillis),
    )
}
