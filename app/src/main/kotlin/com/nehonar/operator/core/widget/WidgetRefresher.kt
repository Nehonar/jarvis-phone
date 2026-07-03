package com.nehonar.operator.core.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Fuerza el refresco del widget cuando cambian los datos que muestra. */
interface WidgetRefresher {
    suspend fun refresh()
}

class GlanceWidgetRefresher @Inject constructor(
    @ApplicationContext private val context: Context,
) : WidgetRefresher {

    override suspend fun refresh() {
        OperatorWidget().updateAll(context)
    }
}
