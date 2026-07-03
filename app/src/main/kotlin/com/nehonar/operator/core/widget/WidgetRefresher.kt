package com.nehonar.operator.core.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.nehonar.operator.core.common.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Fuerza el refresco del widget cuando cambian los datos que muestra. */
interface WidgetRefresher {
    fun refresh()
}

/**
 * Lanza el refresco en el scope de aplicación, no en el del llamante: si el
 * usuario navega justo después de la acción (p. ej. DONE y atrás), el
 * viewModelScope se cancela y el widget se quedaría con el estado viejo.
 */
class GlanceWidgetRefresher @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
) : WidgetRefresher {

    override fun refresh() {
        scope.launch {
            OperatorWidget().updateAll(context)
        }
    }
}
