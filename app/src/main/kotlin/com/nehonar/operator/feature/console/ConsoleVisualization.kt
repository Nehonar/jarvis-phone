package com.nehonar.operator.feature.console

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.nehonar.operator.core.design.theme.OperatorColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

private const val PARTICLE_COUNT = 40
private const val TAU = (PI * 2).toFloat()

private data class Particle(
    val x: Float,
    val y: Float,
    val driftX: Float,
    val driftY: Float,
    val radius: Float,
    val twinkleSpeed: Float,
    val phase: Float,
)

private data class OrbitSpec(
    val radiusFraction: Float,
    val speed: Float,
    val phase: Float,
    val color: Color,
    val size: Float,
)

/**
 * Escena viva de la consola: partículas a la deriva, núcleo pulsante y un nodo
 * orbitando por cada dato real (recordatorio/acción/nota). Todo se dibuja en un
 * único Canvas redibujado por frame; el loop se cancela al salir de la pantalla.
 */
@Composable
fun ConsoleVisualization(
    state: ConsoleUiState,
    modifier: Modifier = Modifier,
) {
    var timeSeconds by remember { mutableFloatStateOf(0f) }
    var burstStart by remember { mutableFloatStateOf(Float.NEGATIVE_INFINITY) }

    LaunchedEffect(Unit) {
        val startNanos = withFrameNanos { it }
        while (true) {
            withFrameNanos { now -> timeSeconds = (now - startNanos) / 1_000_000_000f }
        }
    }

    // Escena estable entre recomposiciones: aleatoriedad con semilla fija.
    val particles = remember {
        val random = Random(seed = 42)
        List(PARTICLE_COUNT) {
            Particle(
                x = random.nextFloat(),
                y = random.nextFloat(),
                driftX = (random.nextFloat() - 0.5f) * 0.012f,
                driftY = (random.nextFloat() - 0.5f) * 0.012f,
                radius = 0.6f + random.nextFloat() * 1.2f,
                twinkleSpeed = 0.4f + random.nextFloat() * 1.4f,
                phase = random.nextFloat() * TAU,
            )
        }
    }
    val orbits = remember(state.nodes) {
        val random = Random(seed = state.nodes.hashCode())
        state.nodes.map { node ->
            val (radiusFraction, baseSpeed, color) = when (node.kind) {
                NodeKind.REMINDER -> Triple(0.34f, 0.55f, OperatorColors.Warning)
                NodeKind.ACTION -> Triple(0.55f, 0.38f, OperatorColors.Cyan)
                NodeKind.NOTE -> Triple(0.78f, 0.24f, OperatorColors.Phosphor)
            }
            OrbitSpec(
                radiusFraction = radiusFraction + (random.nextFloat() - 0.5f) * 0.06f,
                speed = baseSpeed * (0.8f + random.nextFloat() * 0.4f) * (if (random.nextBoolean()) 1f else -1f),
                phase = random.nextFloat() * TAU,
                color = color,
                size = 2.5f + random.nextFloat() * 1.5f,
            )
        }
    }

    Canvas(
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures { burstStart = timeSeconds }
        },
    ) {
        val t = timeSeconds
        val center = Offset(size.width / 2f, size.height / 2f)
        val maxRadius = min(size.width, size.height) / 2f
        val speedFactor = 0.6f + state.activityLevel * 1.4f

        drawParticles(particles, t)
        drawStaticRings(center, maxRadius)
        drawOrbitingNodes(orbits, center, maxRadius, t * speedFactor)
        drawCore(center, maxRadius, t, state.activityLevel)
        drawPulse(center, maxRadius, t, state.activityLevel)
        drawBurst(center, maxRadius, t - burstStart)
    }
}

private fun DrawScope.drawParticles(particles: List<Particle>, t: Float) {
    particles.forEach { p ->
        // Deriva lenta con envolvente [0,1) para reaparecer por el lado contrario.
        val x = ((p.x + p.driftX * t) % 1f + 1f) % 1f * size.width
        val y = ((p.y + p.driftY * t) % 1f + 1f) % 1f * size.height
        val twinkle = 0.35f + 0.65f * (0.5f + 0.5f * sin(t * p.twinkleSpeed + p.phase))
        drawCircle(
            color = OperatorColors.TextDim.copy(alpha = 0.28f * twinkle),
            radius = p.radius.dp.toPx() / 2f,
            center = Offset(x, y),
        )
    }
}

private fun DrawScope.drawStaticRings(center: Offset, maxRadius: Float) {
    val thin = 1.dp.toPx()
    listOf(0.34f, 0.55f, 0.78f).forEach { fraction ->
        drawCircle(
            color = OperatorColors.GridLine.copy(alpha = 0.8f),
            radius = maxRadius * fraction,
            center = center,
            style = Stroke(width = thin),
        )
    }
}

private fun DrawScope.drawOrbitingNodes(
    orbits: List<OrbitSpec>,
    center: Offset,
    maxRadius: Float,
    t: Float,
) {
    orbits.forEach { orbit ->
        val angle = orbit.phase + t * orbit.speed
        val position = center + Offset(
            cos(angle) * maxRadius * orbit.radiusFraction,
            sin(angle) * maxRadius * orbit.radiusFraction,
        )
        val flicker = 0.10f + 0.10f * (0.5f + 0.5f * sin(t * 2.3f + orbit.phase))
        drawLine(
            color = orbit.color.copy(alpha = flicker),
            start = center,
            end = position,
            strokeWidth = 1.dp.toPx() * 0.8f,
        )
        drawCircle(
            color = orbit.color.copy(alpha = 0.18f),
            radius = orbit.size.dp.toPx() * 2.2f,
            center = position,
        )
        drawCircle(
            color = orbit.color,
            radius = orbit.size.dp.toPx(),
            center = position,
        )
    }
}

private fun DrawScope.drawCore(center: Offset, maxRadius: Float, t: Float, activity: Float) {
    val breath = 0.5f + 0.5f * sin(t * (1.2f + activity * 1.6f))
    val coreRadius = maxRadius * (0.085f + 0.02f * breath)
    drawCircle(
        color = OperatorColors.Phosphor.copy(alpha = 0.16f + 0.10f * breath),
        radius = coreRadius * 2.1f,
        center = center,
    )
    drawCircle(
        color = OperatorColors.Phosphor.copy(alpha = 0.85f + 0.15f * breath),
        radius = coreRadius,
        center = center,
    )
}

private fun DrawScope.drawPulse(center: Offset, maxRadius: Float, t: Float, activity: Float) {
    // Más actividad, pulsos más frecuentes: de uno cada 3.2s (vacía) a uno cada 1.2s.
    val period = 3.2f - 2.0f * activity
    val progress = (t / period) % 1f
    drawCircle(
        color = OperatorColors.Phosphor.copy(alpha = (1f - progress) * 0.4f),
        radius = maxRadius * (0.10f + 0.90f * progress),
        center = center,
        style = Stroke(width = 2.dp.toPx()),
    )
}

private fun DrawScope.drawBurst(center: Offset, maxRadius: Float, sinceBurst: Float) {
    val duration = 0.9f
    if (sinceBurst < 0f || sinceBurst > duration) return
    val progress = sinceBurst / duration
    drawCircle(
        color = OperatorColors.Cyan.copy(alpha = (1f - progress) * 0.55f),
        radius = maxRadius * progress,
        center = center,
        style = Stroke(width = 3.dp.toPx() * (1f - progress) + 1.dp.toPx()),
    )
}
