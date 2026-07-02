package com.nehonar.operator.core.design.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

@Composable
fun OperatorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = OperatorColors.Phosphor,
            onPrimary = OperatorColors.Background,
            secondary = OperatorColors.Cyan,
            onSecondary = OperatorColors.Background,
            background = OperatorColors.Background,
            onBackground = OperatorColors.TextPrimary,
            surface = OperatorColors.Surface,
            onSurface = OperatorColors.TextPrimary,
            surfaceVariant = OperatorColors.Surface,
            onSurfaceVariant = OperatorColors.TextDim,
            outline = OperatorColors.GridLine,
            error = OperatorColors.Danger,
            onError = OperatorColors.Background,
        ),
        typography = OperatorTypography,
        content = content,
    )
}
