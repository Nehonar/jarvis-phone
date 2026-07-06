package com.nehonar.operator.feature.conversation

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nehonar.operator.core.design.components.BlinkingCursor
import com.nehonar.operator.core.design.components.ConsolePanel
import com.nehonar.operator.core.design.components.OperatorButton
import com.nehonar.operator.core.design.components.StatusLine
import com.nehonar.operator.core.design.theme.OperatorColors
import com.nehonar.operator.feature.console.ConsoleViewModel
import com.nehonar.operator.feature.console.ConsoleVisualization

@Composable
fun ConversationScreen(
    onNavigate: (OperatorDestination) -> Unit,
    autoStartListening: Boolean = false,
    viewModel: ConversationViewModel = hiltViewModel(),
    consoleViewModel: ConsoleViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val consoleState by consoleViewModel.uiState.collectAsStateWithLifecycle()
    val speaking by consoleViewModel.isSpeaking.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        viewModel.navigation.collect { onNavigate(it) }
    }
    LaunchedEffect(state.turns.size, state.mode) {
        if (state.mode == ConversationMode.SILENCE && state.turns.isNotEmpty()) {
            listState.animateScrollToItem(state.turns.lastIndex)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) viewModel.startTalking() else viewModel.onPermissionDenied()
    }
    val onTalkClick = {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) viewModel.startTalking() else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    // Lanzado desde el asistente del sistema (Fase 16): empieza a escuchar solo.
    LaunchedEffect(autoStartListening) {
        if (autoStartListening) onTalkClick()
    }

    // Escucha continua por frase de activación (D-021): solo con la pantalla en
    // primer plano y con permiso de micrófono. Se corta al salir.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    val micGranted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO,
                    ) == PackageManager.PERMISSION_GRANTED
                    if (micGranted) viewModel.startHandsFreeIfEnabled()
                }
                Lifecycle.Event.ON_PAUSE -> viewModel.stopHandsFree()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val speakMode = state.mode == ConversationMode.SPEAK

    Box(Modifier.fillMaxSize()) {
        // En modo hablar la nube ocupa toda la pantalla (solo se ve el último
        // mensaje del operador encima). En modo texto es una cabecera compacta.
        if (speakMode) {
            ConsoleVisualization(
                state = consoleState,
                speaking = speaking,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(16.dp),
        ) {
            HeaderRow(mode = state.mode, onToggleMute = viewModel::toggleMute)

            if (speakMode) {
                Spacer(Modifier.weight(1f))
                LastOperatorMessage(state)
            } else {
                Spacer(Modifier.height(12.dp))
                ConsoleVisualization(
                    state = consoleState,
                    speaking = speaking,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                )
                Spacer(Modifier.height(12.dp))
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (state.turns.isEmpty()) {
                        Text(
                            text = "Escríbeme, señor. Puedo anotar, recordar, buscar cerca\n" +
                                "o abrir cualquier sección si me la pides.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = OperatorColors.TextDim,
                            modifier = Modifier.align(Alignment.Center).padding(16.dp),
                        )
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(state.turns, key = { it.id }) { turn -> TurnBubble(turn) }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            StatusBar(state.status)

            Spacer(Modifier.height(8.dp))
            when (state.status) {
                is ConversationStatus.Listening, ConversationStatus.Processing -> {
                    OperatorButton(
                        text = if (state.status is ConversationStatus.Processing) "PROCESANDO…" else "ESCUCHANDO… (CANCELAR)",
                        onClick = viewModel::cancelListening,
                        modifier = Modifier.fillMaxWidth(),
                        accent = OperatorColors.Warning,
                    )
                }
                else -> {
                    val wake = state.wake
                    when {
                        wake != null -> WakeIndicator(wake)
                        speakMode -> OperatorButton(
                            text = "HABLAR",
                            onClick = onTalkClick,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        else -> TextComposer(onSend = viewModel::sendText)
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderRow(mode: ConversationMode, onToggleMute: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "OPERATOR",
            style = MaterialTheme.typography.titleLarge,
            color = OperatorColors.Phosphor,
        )
        Spacer(Modifier.width(8.dp))
        BlinkingCursor()
        Spacer(Modifier.weight(1f))
        val silence = mode == ConversationMode.SILENCE
        OperatorButton(
            text = if (silence) "🔇 TEXTO" else "🔊 VOZ",
            onClick = onToggleMute,
            accent = if (silence) OperatorColors.TextDim else OperatorColors.Phosphor,
        )
    }
}

/** En modo hablar solo se muestra la última respuesta del operador, grande y centrada. */
@Composable
private fun LastOperatorMessage(state: ConversationUiState) {
    val last = state.turns.lastOrNull { it.author == Author.OPERATOR }
    if (last == null) {
        Text(
            text = "Pulsa HABLAR y dime algo, señor.",
            style = MaterialTheme.typography.bodyLarge,
            color = OperatorColors.TextDim,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        return
    }
    ConsolePanel(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = last.text,
            style = MaterialTheme.typography.titleMedium,
            color = OperatorColors.Phosphor,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        last.cards.forEach { card ->
            Spacer(Modifier.height(8.dp))
            ResultCardView(card)
        }
    }
}

@Composable
private fun WakeIndicator(wake: WakeState) {
    val active = wake == WakeState.ACTIVE
    StatusLine(
        "ESCUCHA CONTINUA",
        if (active) "LE ESCUCHO…" else "STANDBY · DI TU FRASE",
        valueColor = if (active) OperatorColors.Phosphor else OperatorColors.TextDim,
    )
}

@Composable
private fun StatusBar(status: ConversationStatus) {
    when (status) {
        ConversationStatus.Idle -> StatusLine("OPERATOR", "EN ESPERA")
        is ConversationStatus.Listening -> {
            StatusLine("OPERATOR", "ESCUCHANDO", valueColor = OperatorColors.Phosphor)
            if (status.partial.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(status.partial, style = MaterialTheme.typography.bodySmall, color = OperatorColors.TextPrimary)
            }
        }
        ConversationStatus.Processing ->
            StatusLine("OPERATOR", "INTERPRETANDO", valueColor = OperatorColors.Cyan)
        is ConversationStatus.AwaitingAnswer ->
            StatusLine("OPERATOR", "ESPERANDO DATO", valueColor = OperatorColors.Warning)
        is ConversationStatus.AwaitingConfirmation ->
            StatusLine("OPERATOR", "CONFIRMA SÍ / NO", valueColor = OperatorColors.Warning)
        is ConversationStatus.Error -> {
            StatusLine("OPERATOR", "ERROR", valueColor = OperatorColors.Danger)
            Spacer(Modifier.height(4.dp))
            Text(status.message, style = MaterialTheme.typography.bodySmall, color = OperatorColors.Warning)
        }
    }
}

@Composable
private fun TextComposer(onSend: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.weight(1f),
            singleLine = true,
            placeholder = { Text("escribe al operador…") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = OperatorColors.TextPrimary,
                unfocusedTextColor = OperatorColors.TextPrimary,
                focusedBorderColor = OperatorColors.Phosphor,
                unfocusedBorderColor = OperatorColors.GridLine,
                cursorColor = OperatorColors.Phosphor,
            ),
        )
        Spacer(Modifier.width(8.dp))
        OperatorButton(
            text = "ENVIAR",
            onClick = {
                onSend(text)
                text = ""
            },
        )
    }
}

@Composable
private fun TurnBubble(turn: ConversationTurn) {
    val isUser = turn.author == Author.USER
    val accent = if (isUser) OperatorColors.Cyan else OperatorColors.Phosphor
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = if (isUser) "USTED" else "OPERATOR",
            style = MaterialTheme.typography.labelSmall,
            color = accent,
            modifier = Modifier.fillMaxWidth(),
            textAlign = if (isUser) TextAlign.End else TextAlign.Start,
        )
        Spacer(Modifier.height(2.dp))
        ConsolePanel(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = turn.text,
                style = MaterialTheme.typography.bodyMedium,
                color = OperatorColors.TextPrimary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = if (isUser) TextAlign.End else TextAlign.Start,
            )
            turn.cards.forEach { card ->
                Spacer(Modifier.height(8.dp))
                ResultCardView(card)
            }
        }
    }
}

@Composable
private fun ResultCardView(card: ResultCard) {
    when (card) {
        is ResultCard.Reminder -> CardLine("⏰ RECORDATORIO", "${card.message}  ·  ${card.whenLabel}")
        is ResultCard.PlaceReminder -> CardLine("📍 AL LLEGAR", "${card.message}  ·  ${card.place}")
        is ResultCard.Checklist -> CardLine("✅ CHECKLIST", card.labels.joinToString(", "))
        is ResultCard.Memory -> CardLine("🧠 MEMORIA", card.facts.joinToString("; "))
        is ResultCard.NearbySearch -> CardLine("🗺️ CERCA", card.query)
    }
}

@Composable
private fun CardLine(label: String, value: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = OperatorColors.Cyan)
        Text(value, style = MaterialTheme.typography.bodySmall, color = OperatorColors.TextPrimary)
    }
}
