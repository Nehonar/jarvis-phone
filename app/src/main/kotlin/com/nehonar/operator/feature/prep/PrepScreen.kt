package com.nehonar.operator.feature.prep

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nehonar.operator.core.design.components.ConsolePanel
import com.nehonar.operator.core.design.components.OperatorButton
import com.nehonar.operator.core.design.theme.OperatorColors
import com.nehonar.operator.core.domain.model.ChecklistItem

@Composable
fun PrepScreen(
    onBack: () -> Unit,
    viewModel: PrepViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text(
            text = "OPERATOR // PREP",
            style = MaterialTheme.typography.titleLarge,
            color = OperatorColors.Phosphor,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "OPEN: ${state.openCount} // DONE: ${state.doneCount}",
            style = MaterialTheme.typography.bodySmall,
            color = OperatorColors.TextDim,
        )

        Spacer(Modifier.height(16.dp))
        if (state.groups.isEmpty()) {
            ConsolePanel(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "NO ITEMS // AWAITING INPUT",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OperatorColors.TextDim,
                )
            }
            Spacer(Modifier.weight(1f))
        } else {
            LazyColumn(Modifier.weight(1f)) {
                state.groups.forEach { group ->
                    item(key = "header-${group.type.name}") {
                        ConsolePanel(title = group.type.name, modifier = Modifier.fillMaxWidth()) {
                            group.items.forEachIndexed { index, checklistItem ->
                                if (index > 0) Spacer(Modifier.height(8.dp))
                                ChecklistRow(
                                    item = checklistItem,
                                    onToggle = { viewModel.toggle(checklistItem.id) },
                                    onDelete = { viewModel.delete(checklistItem.id) },
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }

        if (state.doneCount > 0) {
            Spacer(Modifier.height(4.dp))
            OperatorButton(
                text = "CLEAR DONE",
                onClick = viewModel::clearDone,
                modifier = Modifier.fillMaxWidth(),
                accent = OperatorColors.Warning,
            )
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
private fun ChecklistRow(
    item: ChecklistItem,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = if (item.done) "[x]" else "[ ]",
            style = MaterialTheme.typography.bodyMedium,
            color = if (item.done) OperatorColors.Phosphor else OperatorColors.Cyan,
            modifier = Modifier.clickable(onClick = onToggle),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = item.label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (item.done) OperatorColors.TextDim else OperatorColors.TextPrimary,
            textDecoration = if (item.done) TextDecoration.LineThrough else null,
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onToggle),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = "[X]",
            style = MaterialTheme.typography.labelMedium,
            color = OperatorColors.Danger,
            modifier = Modifier.clickable(onClick = onDelete),
        )
    }
}
