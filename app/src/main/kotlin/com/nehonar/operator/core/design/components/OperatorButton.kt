package com.nehonar.operator.core.design.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import com.nehonar.operator.core.design.theme.OperatorColors

@Composable
fun OperatorButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = OperatorColors.Phosphor,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        shape = RectangleShape,
        border = BorderStroke(1.dp, accent),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = accent),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text = "[ ${text.uppercase()} ]",
            style = MaterialTheme.typography.labelMedium,
        )
    }
}
