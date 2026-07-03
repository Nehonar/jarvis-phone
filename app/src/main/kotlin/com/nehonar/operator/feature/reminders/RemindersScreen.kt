package com.nehonar.operator.feature.reminders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nehonar.operator.core.design.components.ConsolePanel
import com.nehonar.operator.core.design.components.OperatorButton
import com.nehonar.operator.core.design.theme.OperatorColors

@Composable
fun RemindersScreen(
    onBack: () -> Unit,
    viewModel: RemindersViewModel = hiltViewModel(),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()

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
                        Text(
                            text = item.header,
                            style = MaterialTheme.typography.bodySmall,
                            color = OperatorColors.TextDim,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = item.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = OperatorColors.TextPrimary,
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OperatorButton(
                                text = "DONE",
                                onClick = { viewModel.markDone(item.id) },
                                modifier = Modifier.weight(1f),
                            )
                            OperatorButton(
                                text = "POSPONER",
                                onClick = { viewModel.postpone(item.id) },
                                modifier = Modifier.weight(1f),
                                accent = OperatorColors.Cyan,
                            )
                            OperatorButton(
                                text = "DESCARTAR",
                                onClick = { viewModel.dismiss(item.id) },
                                modifier = Modifier.weight(1f),
                                accent = OperatorColors.Danger,
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
