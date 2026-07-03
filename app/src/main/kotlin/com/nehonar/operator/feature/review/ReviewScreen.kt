package com.nehonar.operator.feature.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nehonar.operator.core.ai.ActionItem
import com.nehonar.operator.core.ai.Priority
import com.nehonar.operator.core.ai.ReminderDraft
import com.nehonar.operator.core.design.components.ConsolePanel
import com.nehonar.operator.core.design.components.OperatorButton
import com.nehonar.operator.core.design.components.StatusLine
import com.nehonar.operator.core.design.theme.OperatorColors

@Composable
fun ReviewScreen(
    onDone: () -> Unit,
    viewModel: ReviewViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state) {
        if (state is ReviewUiState.Done) onDone()
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text(
            text = "OPERATOR // REVIEW",
            style = MaterialTheme.typography.titleLarge,
            color = OperatorColors.Phosphor,
        )
        Spacer(Modifier.height(16.dp))

        when (val s = state) {
            ReviewUiState.Loading -> {
                Text(
                    text = "CARGANDO...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OperatorColors.TextDim,
                )
            }
            ReviewUiState.NotFound -> {
                ConsolePanel(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "NOTA NO ENCONTRADA",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OperatorColors.Danger,
                    )
                }
                Spacer(Modifier.height(12.dp))
                OperatorButton(text = "BACK", onClick = onDone, modifier = Modifier.fillMaxWidth())
            }
            ReviewUiState.Done -> Unit
            is ReviewUiState.Content -> ReviewContent(
                state = s,
                onAccept = viewModel::accept,
                onDiscard = viewModel::discard,
                onStartEditing = viewModel::startEditing,
                onCancelEditing = viewModel::cancelEditing,
                onSaveEditedText = viewModel::saveEditedText,
            )
        }
    }
}

@Composable
private fun ReviewContent(
    state: ReviewUiState.Content,
    onAccept: () -> Unit,
    onDiscard: () -> Unit,
    onStartEditing: () -> Unit,
    onCancelEditing: () -> Unit,
    onSaveEditedText: (String) -> Unit,
) {
    val intent = state.intent

    if (state.isEditing) {
        var editedText by remember(state.transcript) { mutableStateOf(state.transcript) }
        ConsolePanel(title = "EDITAR TEXTO", modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = editedText,
                onValueChange = { editedText = it },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = OperatorColors.TextPrimary,
                    unfocusedTextColor = OperatorColors.TextPrimary,
                    focusedBorderColor = OperatorColors.Phosphor,
                    unfocusedBorderColor = OperatorColors.GridLine,
                    cursorColor = OperatorColors.Phosphor,
                ),
            )
            state.editError?.let { error ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = OperatorColors.Warning,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OperatorButton(
                text = "RE-PARSE",
                onClick = { onSaveEditedText(editedText) },
                modifier = Modifier.weight(1f),
            )
            OperatorButton(
                text = "CANCEL",
                onClick = onCancelEditing,
                modifier = Modifier.weight(1f),
                accent = OperatorColors.TextDim,
            )
        }
        return
    }

    ConsolePanel(title = intent.intentType.name, modifier = Modifier.fillMaxWidth()) {
        StatusLine("CONFIDENCE", "%.0f%%".format(intent.confidence * 100))
        Spacer(Modifier.height(6.dp))
        Text(
            text = intent.assistantResponse,
            style = MaterialTheme.typography.bodyMedium,
            color = OperatorColors.Phosphor,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = state.transcript,
            style = MaterialTheme.typography.bodySmall,
            color = OperatorColors.TextDim,
        )
    }

    if (intent.clarifyingQuestions.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        ConsolePanel(title = "PREGUNTA PENDIENTE", modifier = Modifier.fillMaxWidth()) {
            intent.clarifyingQuestions.forEach { q ->
                Text(
                    text = q.question,
                    style = MaterialTheme.typography.bodyMedium,
                    color = OperatorColors.Warning,
                )
            }
        }
    }

    if (intent.actions.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        ConsolePanel(title = "ACTIONS", modifier = Modifier.fillMaxWidth()) {
            intent.actions.forEach { action -> ActionRow(action) }
        }
    }

    if (intent.reminders.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        ConsolePanel(title = "REMINDERS", modifier = Modifier.fillMaxWidth()) {
            intent.reminders.forEach { reminder -> ReminderRow(reminder) }
        }
    }

    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OperatorButton(
            text = "ACCEPT",
            onClick = onAccept,
            modifier = Modifier.weight(1f),
        )
        OperatorButton(
            text = "EDIT",
            onClick = onStartEditing,
            modifier = Modifier.weight(1f),
            accent = OperatorColors.Cyan,
        )
        OperatorButton(
            text = "DISCARD",
            onClick = onDiscard,
            modifier = Modifier.weight(1f),
            accent = OperatorColors.Danger,
        )
    }
}

@Composable
private fun ActionRow(action: ActionItem) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            text = action.type.name,
            style = MaterialTheme.typography.labelMedium,
            color = action.priority.toColor(),
            modifier = Modifier.padding(end = 8.dp),
        )
        Text(
            text = action.label,
            style = MaterialTheme.typography.bodyMedium,
            color = OperatorColors.TextPrimary,
        )
    }
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun ReminderRow(reminder: ReminderDraft) {
    Text(
        text = "${reminder.trigger.name}: ${reminder.message}",
        style = MaterialTheme.typography.bodyMedium,
        color = OperatorColors.TextPrimary,
    )
    Spacer(Modifier.height(4.dp))
}

private fun Priority.toColor() = when (this) {
    Priority.HIGH -> OperatorColors.Danger
    Priority.MEDIUM -> OperatorColors.Warning
    Priority.LOW -> OperatorColors.TextDim
}
