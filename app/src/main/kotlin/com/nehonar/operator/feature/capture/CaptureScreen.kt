package com.nehonar.operator.feature.capture

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nehonar.operator.core.design.components.BlinkingCursor
import com.nehonar.operator.core.design.components.ConsolePanel
import com.nehonar.operator.core.design.components.OperatorButton
import com.nehonar.operator.core.design.components.PulseRing
import com.nehonar.operator.core.design.components.StatusLine
import com.nehonar.operator.core.design.theme.OperatorColors

@Composable
fun CaptureScreen(
    onBack: () -> Unit,
    onCaptured: (voiceNoteId: String) -> Unit,
    onOpenHistory: () -> Unit,
    viewModel: CaptureViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(state) {
        val current = state
        if (current is CaptureUiState.Parsed) {
            onCaptured(current.voiceNoteId)
            viewModel.onNavigatedToReview()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) viewModel.startCapture() else viewModel.onPermissionDenied()
    }

    val onStartClick = {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) viewModel.startCapture() else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "OPERATOR // CAPTURE",
                style = MaterialTheme.typography.titleLarge,
                color = OperatorColors.Phosphor,
            )
            Spacer(Modifier.width(8.dp))
            BlinkingCursor()
        }

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            val listening = state as? CaptureUiState.Listening
            val targetScale = 1f + ((listening?.level ?: 0f) / 30f).coerceIn(0f, 0.35f)
            val scale by animateFloatAsState(targetValue = targetScale, label = "pulseScale")
            PulseRing(
                modifier = Modifier
                    .size(220.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
                color = if (state is CaptureUiState.Error) OperatorColors.Danger else OperatorColors.Phosphor,
            )
        }

        ConsolePanel(title = "INPUT", modifier = Modifier.fillMaxWidth()) {
            when (val s = state) {
                CaptureUiState.Idle -> {
                    StatusLine("MODE", "IDLE")
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "PULSA START Y HABLA",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OperatorColors.TextDim,
                    )
                }
                is CaptureUiState.Listening -> {
                    StatusLine("MODE", "LISTENING", valueColor = OperatorColors.Phosphor)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = s.partialText.ifEmpty { "..." },
                        style = MaterialTheme.typography.bodyMedium,
                        color = OperatorColors.TextPrimary,
                    )
                }
                CaptureUiState.Processing -> {
                    StatusLine("MODE", "PROCESSING", valueColor = OperatorColors.Cyan)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "INTERPRETANDO NOTA...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OperatorColors.TextDim,
                    )
                }
                is CaptureUiState.AwaitingAnswer -> {
                    StatusLine("MODE", "AWAITING ANSWER", valueColor = OperatorColors.Warning)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = s.prompt,
                        style = MaterialTheme.typography.bodyMedium,
                        color = OperatorColors.Warning,
                    )
                }
                is CaptureUiState.Parsed -> {
                    StatusLine("MODE", "PARSED", valueColor = OperatorColors.Cyan)
                }
                is CaptureUiState.SavedPending -> {
                    StatusLine("MODE", "PENDING", valueColor = OperatorColors.Warning)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Nota guardada, señor. ${s.reason}.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OperatorColors.Warning,
                    )
                }
                is CaptureUiState.Error -> {
                    StatusLine("MODE", "ERROR", valueColor = OperatorColors.Danger)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = s.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = OperatorColors.Warning,
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        when (val s = state) {
            CaptureUiState.Idle -> {
                OperatorButton(
                    text = "START VOICE",
                    onClick = onStartClick,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OperatorButton(
                    text = "BACK",
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth(),
                    accent = OperatorColors.TextDim,
                )
            }
            is CaptureUiState.Listening, CaptureUiState.Processing, is CaptureUiState.Parsed -> {
                OperatorButton(
                    text = "CANCEL",
                    onClick = viewModel::cancelCapture,
                    modifier = Modifier.fillMaxWidth(),
                    accent = OperatorColors.Warning,
                )
            }
            is CaptureUiState.AwaitingAnswer -> {
                OperatorButton(
                    text = "RESPONDER",
                    onClick = viewModel::startAnsweringClarification,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OperatorButton(
                    text = "CANCEL",
                    onClick = viewModel::cancelCapture,
                    modifier = Modifier.fillMaxWidth(),
                    accent = OperatorColors.TextDim,
                )
            }
            is CaptureUiState.SavedPending -> {
                OperatorButton(
                    text = "VER EN LOG",
                    onClick = {
                        onOpenHistory()
                        viewModel.onNavigatedToHistory()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OperatorButton(
                    text = "BACK",
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth(),
                    accent = OperatorColors.TextDim,
                )
            }
            is CaptureUiState.Error -> {
                if (s.canRetry) {
                    OperatorButton(
                        text = "RETRY",
                        onClick = onStartClick,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                }
                OperatorButton(
                    text = "BACK",
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth(),
                    accent = OperatorColors.TextDim,
                )
            }
        }
    }
}
