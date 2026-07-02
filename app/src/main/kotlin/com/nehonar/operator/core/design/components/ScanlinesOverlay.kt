package com.nehonar.operator.core.design.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun ScanlinesOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val step = 4.dp.toPx()
        val lineHeight = 1.dp.toPx()
        val lineColor = Color.Black.copy(alpha = 0.10f)
        var y = 0f
        while (y < size.height) {
            drawRect(
                color = lineColor,
                topLeft = Offset(0f, y),
                size = Size(size.width, lineHeight),
            )
            y += step
        }
    }
}
