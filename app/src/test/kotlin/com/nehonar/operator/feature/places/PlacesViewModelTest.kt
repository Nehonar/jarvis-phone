package com.nehonar.operator.feature.places

import com.nehonar.operator.core.domain.model.PlaceReminder
import com.nehonar.operator.core.domain.model.ReminderStatus
import com.nehonar.operator.core.domain.model.SavedPlace
import com.nehonar.operator.core.location.LatLng
import com.nehonar.operator.testing.FakeGeofenceScheduler
import com.nehonar.operator.testing.FakeLocationProvider
import com.nehonar.operator.testing.FakePlaceRepository
import com.nehonar.operator.testing.FakePlaceReminderRepository
import com.nehonar.operator.testing.FixedTimeProvider
import com.nehonar.operator.testing.MainDispatcherRule
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlacesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val placeRepository = FakePlaceRepository()
    private val placeReminderRepository = FakePlaceReminderRepository()
    private val locationProvider = FakeLocationProvider()
    private val geofenceScheduler = FakeGeofenceScheduler()
    private val timeProvider = FixedTimeProvider()

    private fun viewModel() = PlacesViewModel(
        placeRepository = placeRepository,
        placeReminderRepository = placeReminderRepository,
        locationProvider = locationProvider,
        geofenceScheduler = geofenceScheduler,
        timeProvider = timeProvider,
    )

    private fun TestScope.collectState(vm: PlacesViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect {}
        }
    }

    @Test
    fun `guardar ubicacion actual crea el lugar y registra el geofence`() = runTest {
        locationProvider.location = LatLng(40.4168, -3.7038)
        val vm = viewModel()
        collectState(vm)

        vm.saveCurrentLocation("Casa")

        val place = placeRepository.current.values.single()
        assertEquals("Casa", place.label)
        assertEquals(40.4168, place.latitude, 0.0001)
        assertEquals(listOf("Casa"), geofenceScheduler.registered.map { it.label })
    }

    @Test
    fun `sin ubicacion muestra error y no guarda`() = runTest {
        locationProvider.location = null
        val vm = viewModel()
        collectState(vm)

        vm.saveCurrentLocation("Casa")

        assertTrue(placeRepository.current.isEmpty())
        assertEquals(true, vm.uiState.value.error != null)
    }

    @Test
    fun `etiqueta en blanco no hace nada`() = runTest {
        val vm = viewModel()
        collectState(vm)

        vm.saveCurrentLocation("   ")

        assertTrue(placeRepository.current.isEmpty())
    }

    @Test
    fun `al refrescar con permiso concedido re-registra los geofences de los lugares`() = runTest {
        // Lugares guardados antes (p. ej. sin permiso de segundo plano, geofence perdido).
        placeRepository.save(SavedPlace("p1", "Casa", 40.0, -3.0, 150f, Instant.ofEpochMilli(1_000)))
        placeRepository.save(SavedPlace("p2", "Trabajo", 41.0, -3.5, 150f, Instant.ofEpochMilli(1_000)))
        geofenceScheduler.backgroundGranted = true
        val vm = viewModel()
        collectState(vm)

        vm.refreshPermissions()

        assertEquals(true, vm.uiState.value.backgroundLocationGranted)
        assertEquals(setOf("p1", "p2"), geofenceScheduler.registered.map { it.id }.toSet())
    }

    @Test
    fun `al refrescar sin permiso no re-registra nada`() = runTest {
        placeRepository.save(SavedPlace("p1", "Casa", 40.0, -3.0, 150f, Instant.ofEpochMilli(1_000)))
        geofenceScheduler.backgroundGranted = false
        val vm = viewModel()
        collectState(vm)

        vm.refreshPermissions()

        assertEquals(false, vm.uiState.value.backgroundLocationGranted)
        assertTrue(geofenceScheduler.registered.isEmpty())
    }

    @Test
    fun `borrar un lugar retira el geofence y limpia sus recordatorios`() = runTest {
        placeRepository.save(
            SavedPlace("p1", "Casa", 40.0, -3.0, 150f, Instant.ofEpochMilli(1_000)),
        )
        placeReminderRepository.save(
            PlaceReminder("pr1", "n1", "Sacar basura", "p1", "Casa", ReminderStatus.PENDING, Instant.ofEpochMilli(2_000)),
        )
        val vm = viewModel()
        collectState(vm)

        vm.delete("p1")

        assertNull(placeRepository.current["p1"])
        assertTrue(placeReminderRepository.current.isEmpty())
        assertEquals(listOf("p1"), geofenceScheduler.unregistered)
    }
}
