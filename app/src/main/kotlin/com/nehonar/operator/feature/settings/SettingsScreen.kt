package com.nehonar.operator.feature.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nehonar.operator.BuildConfig
import com.nehonar.operator.core.ai.AIProviderType
import com.nehonar.operator.core.design.components.ConsolePanel
import com.nehonar.operator.core.design.components.OperatorButton
import com.nehonar.operator.core.design.components.StatusLine
import com.nehonar.operator.core.design.theme.OperatorColors

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val scanlinesEnabled by viewModel.scanlinesEnabled.collectAsStateWithLifecycle()
    val voiceEnabled by viewModel.voiceEnabled.collectAsStateWithLifecycle()
    val wakeWordEnabled by viewModel.wakeWordEnabled.collectAsStateWithLifecycle()
    val wakePhrase by viewModel.wakePhrase.collectAsStateWithLifecycle()
    val wakeEndPhrase by viewModel.wakeEndPhrase.collectAsStateWithLifecycle()
    val wakeDictation by viewModel.wakeDictation.collectAsStateWithLifecycle()
    val aiState by viewModel.aiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(
            text = "OPERATOR // CONFIG",
            style = MaterialTheme.typography.titleLarge,
            color = OperatorColors.Phosphor,
        )

        Spacer(Modifier.height(20.dp))
        ConsolePanel(title = "DISPLAY", modifier = Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "SCANLINES",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OperatorColors.TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = scanlinesEnabled,
                    onCheckedChange = viewModel::setScanlines,
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = OperatorColors.Phosphor,
                        checkedThumbColor = OperatorColors.Background,
                        uncheckedTrackColor = OperatorColors.Surface,
                        uncheckedThumbColor = OperatorColors.TextDim,
                        uncheckedBorderColor = OperatorColors.GridLine,
                    ),
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        ConsolePanel(title = "VOICE", modifier = Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "VOZ DEL OPERADOR",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OperatorColors.TextPrimary,
                    )
                    Text(
                        text = if (voiceEnabled) "HABLA Y CONFIRMA EN VOZ ALTA" else "SILENCIO // SOLO TEXTO",
                        style = MaterialTheme.typography.bodySmall,
                        color = OperatorColors.TextDim,
                    )
                }
                Switch(
                    checked = voiceEnabled,
                    onCheckedChange = viewModel::setVoice,
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = OperatorColors.Phosphor,
                        checkedThumbColor = OperatorColors.Background,
                        uncheckedTrackColor = OperatorColors.Surface,
                        uncheckedThumbColor = OperatorColors.TextDim,
                        uncheckedBorderColor = OperatorColors.GridLine,
                    ),
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        ConsolePanel(title = "MANOS LIBRES", modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Elige Operator como asistente del dispositivo para abrirlo con el " +
                    "gesto de asistente (incluso bloqueado, según el móvil).",
                style = MaterialTheme.typography.bodySmall,
                color = OperatorColors.TextDim,
            )
            Spacer(Modifier.height(8.dp))
            OperatorButton(
                text = "ELEGIR ASISTENTE DEL SISTEMA",
                onClick = {
                    // Preferimos los ajustes de asistente; si el fabricante no los
                    // expone, caemos a los ajustes generales de la app.
                    val opened = runCatching {
                        context.startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
                    }.isSuccess
                    if (!opened) {
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = android.net.Uri.fromParts("package", context.packageName, null)
                                },
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(16.dp))
        ConsolePanel(title = "ESCUCHA CONTINUA", modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "ENCENDER / APAGAR POR VOZ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OperatorColors.TextPrimary,
                    )
                    Text(
                        text = "Con la app abierta, di la frase de encender y te escuchará varias " +
                            "órdenes seguidas hasta que digas la de apagar (no funciona cerrada).",
                        style = MaterialTheme.typography.bodySmall,
                        color = OperatorColors.TextDim,
                    )
                }
                Switch(
                    checked = wakeWordEnabled,
                    onCheckedChange = viewModel::setWakeWord,
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = OperatorColors.Phosphor,
                        checkedThumbColor = OperatorColors.Background,
                        uncheckedTrackColor = OperatorColors.Surface,
                        uncheckedThumbColor = OperatorColors.TextDim,
                        uncheckedBorderColor = OperatorColors.GridLine,
                    ),
                )
            }
            if (wakeWordEnabled) {
                Spacer(Modifier.height(12.dp))
                WakePhraseField(
                    caption = "FRASE PARA ENCENDER",
                    saved = wakePhrase,
                    recording = wakeDictation.field == WakeField.START,
                    partial = wakeDictation.partial,
                    onSave = viewModel::setWakePhrase,
                    onRecord = { viewModel.dictateWakeField(WakeField.START) },
                    onCancelRecord = viewModel::cancelDictation,
                )

                Spacer(Modifier.height(14.dp))
                WakePhraseField(
                    caption = "FRASE PARA APAGAR",
                    saved = wakeEndPhrase,
                    recording = wakeDictation.field == WakeField.END,
                    partial = wakeDictation.partial,
                    onSave = viewModel::setWakeEndPhrase,
                    onRecord = { viewModel.dictateWakeField(WakeField.END) },
                    onCancelRecord = viewModel::cancelDictation,
                )

                wakeDictation.message?.let { message ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = OperatorColors.Cyan,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        ConsolePanel(title = "AI PROVIDER", modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AIProviderType.entries.forEach { type ->
                    OperatorButton(
                        text = type.displayName,
                        onClick = { viewModel.selectProvider(type) },
                        accent = if (type == aiState.selectedProvider) {
                            OperatorColors.Phosphor
                        } else {
                            OperatorColors.TextDim
                        },
                    )
                }
            }

            if (aiState.selectedProvider != AIProviderType.MOCK) {
                Spacer(Modifier.height(12.dp))
                StatusLine(
                    "API KEY",
                    if (aiState.apiKeyConfigured) "CONFIGURADA" else "NO CONFIGURADA",
                    valueColor = if (aiState.apiKeyConfigured) OperatorColors.Phosphor else OperatorColors.Warning,
                )
                Spacer(Modifier.height(8.dp))

                var apiKeyInput by remember(aiState.selectedProvider) { mutableStateOf("") }
                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = { apiKeyInput = it },
                    label = { Text("NUEVA API KEY") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = operatorTextFieldColors(),
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OperatorButton(
                        text = "GUARDAR CLAVE",
                        onClick = {
                            viewModel.saveApiKey(apiKeyInput)
                            apiKeyInput = ""
                        },
                        modifier = Modifier.weight(1f),
                    )
                    OperatorButton(
                        text = "BORRAR",
                        onClick = viewModel::clearApiKey,
                        modifier = Modifier.weight(1f),
                        accent = OperatorColors.Danger,
                    )
                }

                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = aiState.model,
                    onValueChange = viewModel::setModelText,
                    label = { Text("MODELO") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    modifier = Modifier.fillMaxWidth(),
                    colors = operatorTextFieldColors(),
                )
                Spacer(Modifier.height(8.dp))
                OperatorButton(
                    text = "GUARDAR MODELO",
                    onClick = viewModel::saveModel,
                    modifier = Modifier.fillMaxWidth(),
                )

                aiState.statusMessage?.let { message ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = OperatorColors.Cyan,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        ConsolePanel(title = "SYSTEM", modifier = Modifier.fillMaxWidth()) {
            StatusLine("VERSION", BuildConfig.VERSION_NAME)
            Spacer(Modifier.height(6.dp))
            StatusLine("PHASE", "3 // AI REAL")
            Spacer(Modifier.height(6.dp))
            StatusLine(
                "AI PROVIDER",
                aiState.selectedProvider.displayName,
                valueColor = OperatorColors.Phosphor,
            )
        }

        Spacer(Modifier.height(24.dp))
        OperatorButton(
            text = "BACK",
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
            accent = OperatorColors.TextDim,
        )
    }
}

/**
 * Campo de una frase de la escucha continua: se puede teclear y GUARDAR, o GRABAR
 * por voz (mientras graba muestra el parcial y deja CANCELAR).
 */
@Composable
private fun WakePhraseField(
    caption: String,
    saved: String,
    recording: Boolean,
    partial: String,
    onSave: (String) -> Unit,
    onRecord: () -> Unit,
    onCancelRecord: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = caption,
            style = MaterialTheme.typography.bodyMedium,
            color = OperatorColors.TextPrimary,
        )
        Spacer(Modifier.height(6.dp))
        var input by remember(saved) { mutableStateOf(saved) }
        OutlinedTextField(
            value = if (recording && partial.isNotEmpty()) partial else input,
            onValueChange = { input = it },
            enabled = !recording,
            label = { Text("FRASE") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = operatorTextFieldColors(),
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OperatorButton(
                text = "GUARDAR",
                onClick = { onSave(input) },
                modifier = Modifier.weight(1f),
            )
            if (recording) {
                OperatorButton(
                    text = "CANCELAR",
                    onClick = onCancelRecord,
                    modifier = Modifier.weight(1f),
                    accent = OperatorColors.Danger,
                )
            } else {
                OperatorButton(
                    text = "GRABAR",
                    onClick = onRecord,
                    modifier = Modifier.weight(1f),
                    accent = OperatorColors.Cyan,
                )
            }
        }
        if (recording) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "GRABANDO… DI LA FRASE",
                style = MaterialTheme.typography.bodySmall,
                color = OperatorColors.Warning,
            )
        }
    }
}

@Composable
private fun operatorTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = OperatorColors.TextPrimary,
    unfocusedTextColor = OperatorColors.TextPrimary,
    focusedBorderColor = OperatorColors.Phosphor,
    unfocusedBorderColor = OperatorColors.GridLine,
    focusedLabelColor = OperatorColors.Phosphor,
    unfocusedLabelColor = OperatorColors.TextDim,
    cursorColor = OperatorColors.Phosphor,
)
