package com.nehonar.operator.feature.history

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nehonar.operator.core.design.components.ConsolePanel
import com.nehonar.operator.core.design.components.OperatorButton
import com.nehonar.operator.core.design.theme.OperatorColors
import com.nehonar.operator.core.domain.model.VoiceNoteStatus

@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onOpenReview: (String) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val navigateToReviewId by viewModel.navigateToReviewId.collectAsStateWithLifecycle()

    LaunchedEffect(navigateToReviewId) {
        navigateToReviewId?.let { id ->
            onOpenReview(id)
            viewModel.consumeNavigation()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text(
            text = "OPERATOR // LOG",
            style = MaterialTheme.typography.titleLarge,
            color = OperatorColors.Phosphor,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "NOTES: ${items.size}",
            style = MaterialTheme.typography.bodySmall,
            color = OperatorColors.TextDim,
        )

        Spacer(Modifier.height(16.dp))
        if (items.isEmpty()) {
            ConsolePanel(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "NO NOTES // AWAITING INPUT",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OperatorColors.TextDim,
                )
            }
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
                            if (item.intentType != null) {
                                Text(
                                    text = item.intentType.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OperatorColors.Cyan,
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(
                                text = item.status.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = item.status.toColor(),
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.Top) {
                            Text(
                                text = item.transcript,
                                style = MaterialTheme.typography.bodyMedium,
                                color = OperatorColors.TextPrimary,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = "[X]",
                                style = MaterialTheme.typography.labelMedium,
                                color = OperatorColors.Danger,
                                modifier = Modifier.clickable { viewModel.delete(item.id) },
                            )
                        }
                        if (item.canRetry) {
                            Spacer(Modifier.height(8.dp))
                            OperatorButton(
                                text = "REINTENTAR IA",
                                onClick = { viewModel.retryParsing(item.id) },
                                modifier = Modifier.fillMaxWidth(),
                                accent = OperatorColors.Warning,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        if (items.isEmpty()) {
            Spacer(Modifier.weight(1f))
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

private fun VoiceNoteStatus.toColor(): Color = when (this) {
    VoiceNoteStatus.PENDING -> OperatorColors.Warning
    VoiceNoteStatus.TRANSCRIBED -> OperatorColors.Warning
    VoiceNoteStatus.PARSED -> OperatorColors.Phosphor
    VoiceNoteStatus.FAILED -> OperatorColors.Danger
}
