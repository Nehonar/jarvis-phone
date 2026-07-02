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
    onOpenConsole: () -> Unit,
    onOpenSettings: () -> Unit,
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
            StatusLine("VOICE MODULE", "OFFLINE // PHASE 1", valueColor = OperatorColors.Warning)
        }

        Spacer(Modifier.height(16.dp))
        ConsolePanel(title = "DAY", modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "NO DATA // AWAITING INPUT",
                style = MaterialTheme.typography.bodyMedium,
                color = OperatorColors.TextDim,
            )
        }

        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OperatorButton(
                text = "CONSOLE",
                onClick = onOpenConsole,
                modifier = Modifier.weight(1f),
            )
            OperatorButton(
                text = "CONFIG",
                onClick = onOpenSettings,
                modifier = Modifier.weight(1f),
                accent = OperatorColors.Cyan,
            )
        }
    }
}
