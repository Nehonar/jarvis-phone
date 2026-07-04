package com.nehonar.operator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.nehonar.operator.core.widget.WidgetRefresher
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var widgetRefresher: WidgetRefresher

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OperatorApp()
        }
    }

    override fun onResume() {
        super.onResume()
        // Red de seguridad: cada vez que el usuario entra o vuelve a la app, el
        // widget se sincroniza aunque algún refresco puntual se haya perdido.
        widgetRefresher.refresh()
    }

    override fun onPause() {
        super.onPause()
        // Al salir de la app (típicamente a la pantalla de inicio, donde está el
        // widget) se sincroniza con lo último que el usuario haya cambiado dentro.
        widgetRefresher.refresh()
    }
}
