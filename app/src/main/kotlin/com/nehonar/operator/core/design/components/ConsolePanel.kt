package com.nehonar.operator.core.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nehonar.operator.core.design.theme.OperatorColors

@Composable
fun ConsolePanel(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .border(1.dp, OperatorColors.GridLine)
            .background(OperatorColors.Surface)
            .padding(12.dp),
    ) {
        if (title != null) {
            Text(
                text = "// $title",
                style = MaterialTheme.typography.labelMedium,
                color = OperatorColors.TextDim,
            )
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = OperatorColors.GridLine,
            )
        }
        content()
    }
}
