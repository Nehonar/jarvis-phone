package com.nehonar.operator.core.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.action.clickable
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.nehonar.operator.MainActivity
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

// Glance corre fuera del árbol de Compose de la app: colores propios (mismos
// tokens que OperatorColors, que no puede importarse porque usa MaterialTheme).
private val Background = ColorProvider(Color(0xFF0A0F12))
private val Phosphor = ColorProvider(Color(0xFF00E5A0))
private val Cyan = ColorProvider(Color(0xFF4DC9FF))
private val Warning = ColorProvider(Color(0xFFFFB000))
private val TextPrimary = ColorProvider(Color(0xFFD9E8E3))
private val TextDim = ColorProvider(Color(0xFF6E8288))

class OperatorWidget : GlanceAppWidget() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun widgetStateLoader(): WidgetStateLoader
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val loader = EntryPointAccessors
            .fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
            .widgetStateLoader()
        val state = loader.load()
        provideContent {
            WidgetContent(state)
        }
    }
}

@Composable
private fun WidgetContent(state: OperatorWidgetState) {
    val context = LocalContext.current
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(Background)
            .padding(12.dp)
            .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth()) {
            Text(
                text = "OPERATOR",
                style = TextStyle(
                    color = Phosphor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                ),
                modifier = GlanceModifier.defaultWeight(),
            )
            Text(
                text = "PENDING: ${state.pendingCount}",
                style = TextStyle(color = Cyan, fontSize = 12.sp, fontFamily = FontFamily.Monospace),
            )
        }
        Spacer(GlanceModifier.height(8.dp))
        if (state.nextTimeLabel != null && state.nextMessage != null) {
            Text(
                text = "NEXT: ${state.nextTimeLabel}",
                style = TextStyle(color = Warning, fontSize = 12.sp, fontFamily = FontFamily.Monospace),
            )
            Spacer(GlanceModifier.height(2.dp))
            Text(
                text = state.nextMessage,
                style = TextStyle(color = TextPrimary, fontSize = 13.sp, fontFamily = FontFamily.Monospace),
                maxLines = 2,
            )
        } else {
            Text(
                text = "SIN AVISOS PROGRAMADOS",
                style = TextStyle(color = TextDim, fontSize = 13.sp, fontFamily = FontFamily.Monospace),
            )
        }
        Spacer(GlanceModifier.defaultWeight())
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            Spacer(GlanceModifier.defaultWeight())
            Text(
                text = "[ VOICE ]",
                style = TextStyle(
                    color = Phosphor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                ),
                modifier = GlanceModifier.clickable(actionStartActivity(captureDeepLinkIntent(context))),
            )
            Spacer(GlanceModifier.width(4.dp))
        }
    }
}

// Intent explícito (clase propia) con el URI como data: el deep link no sale
// de la app y NavController lo resuelve a CaptureRoute al abrirse la activity.
private fun captureDeepLinkIntent(context: Context): Intent =
    Intent(Intent.ACTION_VIEW, Uri.parse("operator://capture"), context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

class OperatorWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = OperatorWidget()
}
