package com.nehonar.operator.feature.places

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
fun PlacesScreen(
    onBack: () -> Unit,
    viewModel: PlacesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var label by remember { mutableStateOf("") }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshPermissions()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // ACCESS_BACKGROUND_LOCATION no se puede pedir en el mismo diálogo que FINE:
    // primero FINE en runtime, y para el "todo el tiempo" se envía a Ajustes.
    val fineLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { viewModel.refreshPermissions() }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text(
            text = "OPERATOR // PLACES",
            style = MaterialTheme.typography.titleLarge,
            color = OperatorColors.Phosphor,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "PLACES: ${state.places.size} // SOLO EN ESTE DISPOSITIVO",
            style = MaterialTheme.typography.bodySmall,
            color = OperatorColors.TextDim,
        )

        if (!state.backgroundLocationGranted) {
            Spacer(Modifier.height(16.dp))
            ConsolePanel(title = "PERMISOS", modifier = Modifier.fillMaxWidth()) {
                StatusLine("BACKGROUND LOCATION", "OFF", valueColor = OperatorColors.Warning)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Para avisar al llegar a un lugar hace falta ubicación " +
                        "\"Permitir todo el tiempo\".",
                    style = MaterialTheme.typography.bodySmall,
                    color = OperatorColors.TextDim,
                )
                Spacer(Modifier.height(8.dp))
                OperatorButton(
                    text = "CONCEDER UBICACIÓN",
                    onClick = {
                        fineLocationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                        context.startActivity(appSettingsIntent(context))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    accent = OperatorColors.Warning,
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        ConsolePanel(title = "NUEVO LUGAR", modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("etiqueta: casa, trabajo…") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = OperatorColors.TextPrimary,
                    unfocusedTextColor = OperatorColors.TextPrimary,
                    focusedBorderColor = OperatorColors.Phosphor,
                    unfocusedBorderColor = OperatorColors.GridLine,
                    cursorColor = OperatorColors.Phosphor,
                ),
            )
            state.error?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = OperatorColors.Danger)
            }
            Spacer(Modifier.height(10.dp))
            OperatorButton(
                text = if (state.saving) "GUARDANDO…" else "GUARDAR UBICACIÓN ACTUAL",
                onClick = {
                    viewModel.saveCurrentLocation(label)
                    label = ""
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(16.dp))
        if (state.places.isEmpty()) {
            ConsolePanel(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "SIN LUGARES // Guarde \"casa\" o \"trabajo\" estando allí.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OperatorColors.TextDim,
                )
            }
            Spacer(Modifier.weight(1f))
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(state.places, key = { it.id }) { item ->
                    ConsolePanel(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = item.label.uppercase(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = OperatorColors.Cyan,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = item.coordsLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = OperatorColors.TextDim,
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = "[X]",
                                style = MaterialTheme.typography.labelMedium,
                                color = OperatorColors.Danger,
                                modifier = Modifier.clickable { viewModel.delete(item.id) },
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

// Android exige conceder "Permitir todo el tiempo" desde los ajustes de la app,
// no desde un diálogo runtime (a partir de Android 11).
private fun appSettingsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
