package com.nehonar.operator.feature.reminders

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nehonar.operator.core.design.components.ConsolePanel
import com.nehonar.operator.core.design.components.OperatorButton
import com.nehonar.operator.core.design.components.StatusLine
import com.nehonar.operator.core.design.theme.OperatorColors

@Composable
fun RemindersScreen(
    onBack: () -> Unit,
    viewModel: RemindersViewModel = hiltViewModel(),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val exactAlarmsEnabled by viewModel.exactAlarmsEnabled.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var notificationsEnabled by remember { mutableStateOf(true) }

    // Al volver de Ajustes del sistema, reevalúa los permisos para que el panel
    // desaparezca en cuanto estén concedidos.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
                viewModel.refreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text(
            text = "OPERATOR // REMINDERS",
            style = MaterialTheme.typography.titleLarge,
            color = OperatorColors.Phosphor,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "PENDING: ${items.size}",
            style = MaterialTheme.typography.bodySmall,
            color = OperatorColors.TextDim,
        )

        if (!notificationsEnabled || !exactAlarmsEnabled) {
            Spacer(Modifier.height(16.dp))
            PermissionsPanel(
                context = context,
                notificationsEnabled = notificationsEnabled,
                exactAlarmsEnabled = exactAlarmsEnabled,
            )
        }

        Spacer(Modifier.height(16.dp))
        if (items.isEmpty()) {
            ConsolePanel(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "NO PENDING REMINDERS",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OperatorColors.TextDim,
                )
            }
            Spacer(Modifier.weight(1f))
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(items, key = { it.id }) { item ->
                    ConsolePanel(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = item.header,
                                style = MaterialTheme.typography.bodySmall,
                                color = OperatorColors.TextDim,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = "[X]",
                                style = MaterialTheme.typography.labelMedium,
                                color = OperatorColors.Danger,
                                modifier = Modifier.clickable { viewModel.dismiss(item.id) },
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = item.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = OperatorColors.TextPrimary,
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OperatorButton(
                                text = "DONE",
                                onClick = { viewModel.markDone(item.id) },
                                modifier = Modifier.weight(1f),
                            )
                            OperatorButton(
                                text = "+15 MIN",
                                onClick = { viewModel.postpone(item.id) },
                                modifier = Modifier.weight(1f),
                                accent = OperatorColors.Cyan,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        OperatorButton(
            text = "BACK",
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
            accent = OperatorColors.TextDim,
        )
    }
}

@Composable
private fun PermissionsPanel(
    context: Context,
    notificationsEnabled: Boolean,
    exactAlarmsEnabled: Boolean,
) {
    ConsolePanel(title = "PERMISOS", modifier = Modifier.fillMaxWidth()) {
        if (!notificationsEnabled) {
            StatusLine("NOTIFICATIONS", "OFF", valueColor = OperatorColors.Danger)
            Spacer(Modifier.height(8.dp))
            OperatorButton(
                text = "ACTIVAR NOTIFICACIONES",
                onClick = { context.startActivity(notificationSettingsIntent(context)) },
                modifier = Modifier.fillMaxWidth(),
                accent = OperatorColors.Warning,
            )
        }
        if (!exactAlarmsEnabled) {
            if (!notificationsEnabled) Spacer(Modifier.height(12.dp))
            StatusLine("EXACT ALARMS", "OFF", valueColor = OperatorColors.Warning)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Sin este permiso el aviso puede llegar unos minutos tarde.",
                style = MaterialTheme.typography.bodySmall,
                color = OperatorColors.TextDim,
            )
            Spacer(Modifier.height(8.dp))
            OperatorButton(
                text = "ACTIVAR ALARMAS EXACTAS",
                onClick = { context.startActivity(exactAlarmSettingsIntent(context)) },
                modifier = Modifier.fillMaxWidth(),
                accent = OperatorColors.Warning,
            )
        }
    }
}

// canScheduleExact() solo devuelve false en Android 12+, donde esta action existe.
private fun exactAlarmSettingsIntent(context: Context): Intent = Intent(
    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
    Uri.fromParts("package", context.packageName, null),
)

private fun notificationSettingsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
