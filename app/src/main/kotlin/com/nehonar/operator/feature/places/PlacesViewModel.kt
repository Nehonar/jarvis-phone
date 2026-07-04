package com.nehonar.operator.feature.places

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nehonar.operator.core.common.TimeProvider
import com.nehonar.operator.core.domain.model.SavedPlace
import com.nehonar.operator.core.domain.repository.PlaceReminderRepository
import com.nehonar.operator.core.domain.repository.PlaceRepository
import com.nehonar.operator.core.location.GeofenceScheduler
import com.nehonar.operator.core.location.LocationProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PlaceItem(
    val id: String,
    val label: String,
    val coordsLabel: String,
)

data class PlacesUiState(
    val places: List<PlaceItem> = emptyList(),
    val backgroundLocationGranted: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
)

private const val DEFAULT_RADIUS_METERS = 150f

@HiltViewModel
class PlacesViewModel @Inject constructor(
    private val placeRepository: PlaceRepository,
    private val placeReminderRepository: PlaceReminderRepository,
    private val locationProvider: LocationProvider,
    private val geofenceScheduler: GeofenceScheduler,
    private val timeProvider: TimeProvider,
) : ViewModel() {

    private val transient = MutableStateFlow(
        PlacesUiState(backgroundLocationGranted = geofenceScheduler.canRegisterGeofences()),
    )

    val uiState: StateFlow<PlacesUiState> = combine(
        placeRepository.observeAll().map { places -> places.map { it.toItem() } },
        transient,
    ) { places, state -> state.copy(places = places) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = transient.value,
        )

    fun refreshPermissions() {
        transient.value = transient.value.copy(
            backgroundLocationGranted = geofenceScheduler.canRegisterGeofences(),
        )
    }

    /** Captura la posición actual y la guarda como lugar con la etiqueta dada. */
    fun saveCurrentLocation(label: String) {
        val clean = label.trim()
        if (clean.isEmpty() || transient.value.saving) return
        viewModelScope.launch {
            transient.value = transient.value.copy(saving = true, error = null)
            val loc = locationProvider.currentLocation()
            if (loc == null) {
                transient.value = transient.value.copy(
                    saving = false,
                    error = "Sin ubicación. Revise el permiso y que el GPS esté activo.",
                )
                return@launch
            }
            val place = SavedPlace(
                id = UUID.randomUUID().toString(),
                label = clean,
                latitude = loc.latitude,
                longitude = loc.longitude,
                radiusMeters = DEFAULT_RADIUS_METERS,
                createdAt = timeProvider.now(),
            )
            placeRepository.save(place)
            geofenceScheduler.register(place)
            transient.value = transient.value.copy(saving = false)
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            geofenceScheduler.unregister(id)
            placeReminderRepository.getAllForPlace(id).forEach { placeReminderRepository.delete(it.id) }
            placeRepository.delete(id)
        }
    }
}

private fun SavedPlace.toItem(): PlaceItem = PlaceItem(
    id = id,
    label = label,
    coordsLabel = "%.4f, %.4f".format(latitude, longitude),
)
