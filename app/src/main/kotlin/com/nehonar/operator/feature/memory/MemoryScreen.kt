package com.nehonar.operator.feature.memory

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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nehonar.operator.core.design.components.ConsolePanel
import com.nehonar.operator.core.design.components.OperatorButton
import com.nehonar.operator.core.design.theme.OperatorColors

@Composable
fun MemoryScreen(
    onBack: () -> Unit,
    viewModel: MemoryViewModel = hiltViewModel(),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text(
            text = "OPERATOR // MEMORY",
            style = MaterialTheme.typography.titleLarge,
            color = OperatorColors.Phosphor,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "FACTS: ${items.size} // SOLO EN ESTE DISPOSITIVO",
            style = MaterialTheme.typography.bodySmall,
            color = OperatorColors.TextDim,
        )

        Spacer(Modifier.height(16.dp))
        if (items.isEmpty()) {
            ConsolePanel(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "MEMORIA VACÍA // Diga \"apunta que…\" y acepte la nota.",
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
                                text = item.topic.uppercase(),
                                style = MaterialTheme.typography.bodySmall,
                                color = OperatorColors.Cyan,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = item.dateLabel,
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
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = item.fact,
                            style = MaterialTheme.typography.bodyMedium,
                            color = OperatorColors.TextPrimary,
                        )
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
