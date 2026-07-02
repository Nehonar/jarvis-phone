package com.nehonar.operator.core.design.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.nehonar.operator.core.design.theme.OperatorColors

@Composable
fun PulseRing(
    modifier: Modifier = Modifier,
    color: Color = OperatorColors.Phosphor,
) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = LinearEasing),
        ),
        label = "pulseProgress",
    )
    Canvas(modifier = modifier) {
        val maxRadius = size.minDimension / 2f
        val thin = 1.dp.toPx()
        drawCircle(color = color.copy(alpha = 0.9f), radius = maxRadius * 0.10f)
        drawCircle(
            color = color.copy(alpha = 0.35f),
            radius = maxRadius * 0.45f,
            style = Stroke(width = thin),
        )
        drawCircle(
            color = color.copy(alpha = 0.20f),
            radius = maxRadius * 0.75f,
            style = Stroke(width = thin),
        )
        val pulseRadius = maxRadius * (0.10f + 0.90f * progress)
        drawCircle(
            color = color.copy(alpha = (1f - progress) * 0.5f),
            radius = pulseRadius,
            style = Stroke(width = 2.dp.toPx()),
        )
    }
}
