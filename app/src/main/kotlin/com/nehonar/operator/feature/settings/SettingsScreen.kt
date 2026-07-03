package com.nehonar.operator.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nehonar.operator.BuildConfig
import com.nehonar.operator.core.design.components.ConsolePanel
import com.nehonar.operator.core.design.components.OperatorButton
import com.nehonar.operator.core.design.components.StatusLine
import com.nehonar.operator.core.design.theme.OperatorColors

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val scanlinesEnabled by viewModel.scanlinesEnabled.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text(
            text = "OPERATOR // CONFIG",
            style = MaterialTheme.typography.titleLarge,
            color = OperatorColors.Phosphor,
        )

        Spacer(Modifier.height(20.dp))
        ConsolePanel(title = "DISPLAY", modifier = Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "SCANLINES",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OperatorColors.TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = scanlinesEnabled,
                    onCheckedChange = viewModel::setScanlines,
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = OperatorColors.Phosphor,
                        checkedThumbColor = OperatorColors.Background,
                        uncheckedTrackColor = OperatorColors.Surface,
                        uncheckedThumbColor = OperatorColors.TextDim,
                        uncheckedBorderColor = OperatorColors.GridLine,
                    ),
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        ConsolePanel(title = "SYSTEM", modifier = Modifier.fillMaxWidth()) {
            StatusLine("VERSION", BuildConfig.VERSION_NAME)
            Spacer(Modifier.height(6.dp))
            StatusLine("PHASE", "2 // AI MOCK")
            Spacer(Modifier.height(6.dp))
            StatusLine("AI PROVIDER", "MOCK", valueColor = OperatorColors.Phosphor)
        }

        Spacer(Modifier.weight(1f))
        OperatorButton(
            text = "BACK",
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
            accent = OperatorColors.TextDim,
        )
    }
}
