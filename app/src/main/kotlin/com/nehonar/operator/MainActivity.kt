package com.nehonar.operator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.nehonar.operator.core.common.di.ApplicationScope
import com.nehonar.operator.core.domain.repository.PlaceRepository
import com.nehonar.operator.core.location.GeofenceScheduler
import com.nehonar.operator.core.widget.WidgetRefresher
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var widgetRefresher: WidgetRefresher

    @Inject lateinit var geofenceScheduler: GeofenceScheduler

    @Inject lateinit var placeRepository: PlaceRepository

    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val startInListening = intent?.getBooleanExtra(EXTRA_START_LISTENING, false) == true
        setContent {
            OperatorApp(startInListening = startInListening)
        }
    }

    override fun onResume() {
        super.onResume()
        // Red de seguridad: cada vez que el usuario entra o vuelve a la app, el
        // widget se sincroniza aunque algún refresco puntual se haya perdido.
        widgetRefresher.refresh()
        // Y se re-registran los geofences de los lugares guardados: si un lugar se
        // guardó sin permiso de segundo plano (o el permiso se concedió después),
        // así el aviso al llegar queda activo sin esperar a un reinicio.
        reregisterGeofences()
    }

    override fun onPause() {
        super.onPause()
        // Al salir de la app (típicamente a la pantalla de inicio, donde está el
        // widget) se sincroniza con lo último que el usuario haya cambiado dentro.
        widgetRefresher.refresh()
    }

    private fun reregisterGeofences() {
        if (!geofenceScheduler.canRegisterGeofences()) return
        appScope.launch {
            placeRepository.getAll().forEach { geofenceScheduler.register(it) }
        }
    }

    companion object {
        /** El asistente del sistema pone este extra para que la conversación escuche al abrir. */
        const val EXTRA_START_LISTENING = "operator_start_listening"
    }
}
