package com.nehonar.operator.feature.console

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nehonar.operator.core.design.components.BlinkingCursor
import com.nehonar.operator.core.design.components.ConsolePanel
import com.nehonar.operator.core.design.components.OperatorButton
import com.nehonar.operator.core.design.components.PulseRing
import com.nehonar.operator.core.design.components.StatusLine
import com.nehonar.operator.core.design.theme.OperatorColors

@Composable
fun ConsoleScreen(onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "OPERATOR // CONSOLE",
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
            PulseRing(modifier = Modifier.size(240.dp))
        }

        ConsolePanel(modifier = Modifier.fillMaxWidth()) {
            StatusLine("MODE", "STANDBY", valueColor = OperatorColors.Phosphor)
            Spacer(Modifier.height(6.dp))
            StatusLine("LINK", "LOCAL")
            Spacer(Modifier.height(6.dp))
            StatusLine("VOICE", "READY", valueColor = OperatorColors.Phosphor)
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
