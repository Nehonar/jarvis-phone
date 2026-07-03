package com.nehonar.operator.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nehonar.operator.core.design.components.BlinkingCursor
import com.nehonar.operator.core.design.components.ConsolePanel
import com.nehonar.operator.core.design.components.OperatorButton
import com.nehonar.operator.core.design.components.StatusLine
import com.nehonar.operator.core.design.theme.OperatorColors

@Composable
fun HomeScreen(
    onOpenCapture: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenConsole: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenReminders: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "OPERATOR // DAY STATUS",
                style = MaterialTheme.typography.titleLarge,
                color = OperatorColors.Phosphor,
            )
            Spacer(Modifier.width(8.dp))
            BlinkingCursor()
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = state.dateLabel,
            style = MaterialTheme.typography.bodySmall,
            color = OperatorColors.TextDim,
        )

        Spacer(Modifier.height(20.dp))
        ConsolePanel(title = "STATUS", modifier = Modifier.fillMaxWidth()) {
            StatusLine("SYSTEMS", "ONLINE", valueColor = OperatorColors.Phosphor)
            Spacer(Modifier.height(6.dp))
            StatusLine("LOCAL NOTES", state.noteCount.toString())
            Spacer(Modifier.height(6.dp))
            StatusLine("VOICE MODULE", "ONLINE", valueColor = OperatorColors.Phosphor)
        }

        Spacer(Modifier.height(16.dp))
        ConsolePanel(title = "DAY", modifier = Modifier.fillMaxWidth()) {
            val next = state.nextReminder
            if (next != null) {
                StatusLine("NEXT", next.timeLabel, valueColor = OperatorColors.Warning)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = next.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = OperatorColors.TextPrimary,
                )
            } else {
                Text(
                    text = "SIN AVISOS PROGRAMADOS",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OperatorColors.TextDim,
                )
            }
            Spacer(Modifier.height(8.dp))
            StatusLine("REMINDERS", state.pendingReminders.toString())
            Spacer(Modifier.height(6.dp))
            StatusLine(
                "AWAITING REVIEW",
                state.awaitingReview.toString(),
                valueColor = if (state.awaitingReview > 0) OperatorColors.Warning else OperatorColors.TextPrimary,
            )
        }

        Spacer(Modifier.height(16.dp))
        ConsolePanel(title = "FEED", modifier = Modifier.fillMaxWidth()) {
            if (state.feed.isEmpty()) {
                Text(
                    text = "NO ACTIVITY TODAY // AWAITING INPUT",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OperatorColors.TextDim,
                )
            } else {
                state.feed.forEachIndexed { index, item ->
                    if (index > 0) Spacer(Modifier.height(6.dp))
                    Row {
                        Text(
                            text = item.time,
                            style = MaterialTheme.typography.bodySmall,
                            color = OperatorColors.TextDim,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = item.tag,
                            style = MaterialTheme.typography.bodySmall,
                            color = OperatorColors.Cyan,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = item.text,
                            style = MaterialTheme.typography.bodySmall,
                            color = OperatorColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))
        OperatorButton(
            text = "VOICE",
            onClick = onOpenCapture,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OperatorButton(
            text = "REMINDERS",
            onClick = onOpenReminders,
            modifier = Modifier.fillMaxWidth(),
            accent = OperatorColors.Cyan,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OperatorButton(
                text = "LOG",
                onClick = onOpenHistory,
                modifier = Modifier.weight(1f),
                accent = OperatorColors.Cyan,
            )
            OperatorButton(
                text = "CONSOLE",
                onClick = onOpenConsole,
                modifier = Modifier.weight(1f),
                accent = OperatorColors.Cyan,
            )
            OperatorButton(
                text = "CONFIG",
                onClick = onOpenSettings,
                modifier = Modifier.weight(1f),
                accent = OperatorColors.TextDim,
            )
        }
    }
}
