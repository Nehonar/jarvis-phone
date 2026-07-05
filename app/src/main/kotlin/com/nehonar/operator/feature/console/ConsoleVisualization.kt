package com.nehonar.operator.feature.console

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

private const val POINT_COUNT = 230
private const val TAU = (PI * 2).toFloat()
private const val FOCAL = 2.6f

/** Punto de la nube en coordenadas de esfera unidad (se rota y proyecta por frame). */
private data class CloudPoint(
    val x: Float,
    val y: Float,
    val z: Float,
    val twinkleSpeed: Float,
    val phase: Float,
)

private data class Edge(val a: Int, val b: Int)

/**
 * Nube de partículas viva: cientos de puntos distribuidos en una esfera que gira
 * despacio, con líneas finas entre vecinos (aspecto de nebulosa / red 3D). Cada
 * dato real (recordatorio/acción/nota) ilumina un punto con su color. El brillo,
 * la velocidad de giro y el pulso central crecen con la actividad — y sobre todo
 * cuando el operador [speaking] habla, la nube se enciende y se expande. Todo en
 * un único Canvas; el loop se cancela al salir de la pantalla.
 */
@Composable
fun ConsoleVisualization(
    state: ConsoleUiState,
    modifier: Modifier = Modifier,
    speaking: Boolean = false,
) {
    var timeSeconds by remember { mutableFloatStateOf(0f) }
    var burstStart by remember { mutableFloatStateOf(Float.NEGATIVE_INFINITY) }
    // Energía de voz suavizada (0..1): sube al hablar, baja al callar, sin saltos.
    var voiceEnergy by remember { mutableFloatStateOf(0f) }
    val speakingState = rememberUpdatedState(speaking)

    LaunchedEffect(Unit) {
        val startNanos = withFrameNanos { it }
        var prevNanos = startNanos
        while (true) {
            withFrameNanos { now ->
                timeSeconds = (now - startNanos) / 1_000_000_000f
                val dt = ((now - prevNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
                prevNanos = now
                val target = if (speakingState.value) 1f else 0f
                // Suavizado exponencial (ataque/caída ~0.18s), independiente del framerate.
                voiceEnergy += (target - voiceEnergy) * (dt / (dt + 0.18f))
            }
        }
    }

    // Escena estable entre recomposiciones: aleatoriedad con semilla fija.
    val points = remember { buildCloud() }
    val edges = remember { buildEdges(points) }

    // Índice de nube -> color, uno por cada dato real: ilumina ese punto.
    val highlights = remember(state.nodes) {
        state.nodes.associate { node ->
            val index = (node.id.hashCode().mod(POINT_COUNT))
            index to when (node.kind) {
                NodeKind.REMINDER -> OperatorColors.Warning
                NodeKind.ACTION -> OperatorColors.Cyan
                NodeKind.NOTE -> OperatorColors.Phosphor
            }
        }
    }

    Canvas(
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures { burstStart = timeSeconds }
        },
    ) {
        val t = timeSeconds
        val energy = voiceEnergy
        val center = Offset(size.width / 2f, size.height / 2f)
        // Al hablar, la nube "respira" y se expande un poco (vibración con la voz).
        val expansion = 1f + 0.09f * energy + 0.02f * energy * sin(t * 26f)
        val sphereRadius = min(size.width, size.height) / 2f * 0.82f * expansion
        val brightness = 0.72f + 0.28f * state.activityLevel + 0.55f * energy
        val rotationSpeed = 0.14f + 0.16f * state.activityLevel + 0.30f * energy

        // Rotación de la nube (giro lento en Y + ligero cabeceo en X).
        val ay = t * rotationSpeed
        val ax = 0.42f + 0.10f * sin(t * 0.16f)
        val cosY = cos(ay)
        val sinY = sin(ay)
        val cosX = cos(ax)
        val sinX = sin(ax)

        // Proyección de cada punto a pantalla + profundidad (0 lejos, 1 cerca).
        val projX = FloatArray(points.size)
        val projY = FloatArray(points.size)
        val depth = FloatArray(points.size)
        points.forEachIndexed { i, p ->
            val x1 = p.x * cosY + p.z * sinY
            val z1 = -p.x * sinY + p.z * cosY
            val y2 = p.y * cosX - z1 * sinX
            val z2 = p.y * sinX + z1 * cosX
            val persp = FOCAL / (FOCAL - z2)
            projX[i] = center.x + x1 * sphereRadius * persp
            projY[i] = center.y + y2 * sphereRadius * persp
            depth[i] = ((z2 + 1f) / 2f).coerceIn(0f, 1f)
        }

        drawCoreGlow(center, sphereRadius, t, state.activityLevel + energy)
        drawEdges(edges, projX, projY, depth, brightness)
        drawPoints(points, projX, projY, depth, highlights, t, brightness, energy)
        drawPulse(center, sphereRadius, t, state.activityLevel, energy)
        drawBurst(center, sphereRadius, t - burstStart)
    }
}

private fun buildCloud(): List<CloudPoint> {
    val random = Random(seed = 42)
    return List(POINT_COUNT) {
        // Dirección uniforme en la esfera; radio con densidad de volumen (cbrt),
        // que al proyectar concentra brillo en el centro como una nebulosa.
        val cosTheta = 2f * random.nextFloat() - 1f
        val sinTheta = sqrt((1f - cosTheta * cosTheta).coerceAtLeast(0f))
        val phi = random.nextFloat() * TAU
        val r = random.nextFloat().pow(1f / 3f) * (0.55f + 0.45f * random.nextFloat())
        CloudPoint(
            x = sinTheta * cos(phi) * r,
            y = sinTheta * sin(phi) * r,
            z = cosTheta * r,
            twinkleSpeed = 0.5f + random.nextFloat() * 1.6f,
            phase = random.nextFloat() * TAU,
        )
    }
}

/** Une cada punto con su vecino más cercano: red dispersa, sin O(n²) por frame. */
private fun buildEdges(points: List<CloudPoint>): List<Edge> {
    val seen = HashSet<Long>()
    val edges = ArrayList<Edge>(points.size)
    points.indices.forEach { i ->
        var best = -1
        var bestDist = Float.MAX_VALUE
        points.indices.forEach { j ->
            if (j != i) {
                val dx = points[i].x - points[j].x
                val dy = points[i].y - points[j].y
                val dz = points[i].z - points[j].z
                val d = dx * dx + dy * dy + dz * dz
                if (d < bestDist) {
                    bestDist = d
                    best = j
                }
            }
        }
        if (best >= 0) {
            val key = if (i < best) i.toLong() * POINT_COUNT + best else best.toLong() * POINT_COUNT + i
            if (seen.add(key)) edges.add(Edge(i, best))
        }
    }
    return edges
}

private fun DrawScope.drawEdges(
    edges: List<Edge>,
    projX: FloatArray,
    projY: FloatArray,
    depth: FloatArray,
    brightness: Float,
) {
    val width = 0.8.dp.toPx()
    edges.forEach { edge ->
        val d = (depth[edge.a] + depth[edge.b]) / 2f
        drawLine(
            color = OperatorColors.Phosphor.copy(alpha = 0.05f + 0.10f * d * brightness),
            start = Offset(projX[edge.a], projY[edge.a]),
            end = Offset(projX[edge.b], projY[edge.b]),
            strokeWidth = width,
        )
    }
}

private fun DrawScope.drawPoints(
    points: List<CloudPoint>,
    projX: FloatArray,
    projY: FloatArray,
    depth: FloatArray,
    highlights: Map<Int, Color>,
    t: Float,
    brightness: Float,
    energy: Float,
) {
    // De atrás hacia delante, para que los cercanos queden por encima.
    val order = points.indices.sortedBy { depth[it] }
    // Al hablar, los puntos titilan más rápido y crecen un poco: la nube "vibra".
    val sizeBoost = 1f + 0.6f * energy
    order.forEach { i ->
        val d = depth[i]
        val speed = points[i].twinkleSpeed * (1f + 2.2f * energy)
        val twinkle = 0.6f + 0.4f * (0.5f + 0.5f * sin(t * speed + points[i].phase))
        val pos = Offset(projX[i], projY[i])
        val radius = (0.7f + 1.6f * d).dp.toPx() * sizeBoost
        val highlight = highlights[i]
        if (highlight == null) {
            drawCircle(
                color = OperatorColors.Phosphor.copy(
                    alpha = ((0.12f + 0.5f * d) * twinkle * brightness).coerceAtMost(1f),
                ),
                radius = radius,
                center = pos,
            )
        } else {
            val pulse = 0.6f + 0.4f * (0.5f + 0.5f * sin(t * 2.4f + points[i].phase))
            drawCircle(
                color = highlight.copy(alpha = 0.16f * pulse),
                radius = radius * 3.4f,
                center = pos,
            )
            drawCircle(
                color = highlight.copy(alpha = (0.75f + 0.25f * d) * pulse),
                radius = radius * 1.7f,
                center = pos,
            )
        }
    }
}

private fun DrawScope.drawCoreGlow(center: Offset, sphereRadius: Float, t: Float, activity: Float) {
    val breath = 0.5f + 0.5f * sin(t * (0.9f + activity * 1.4f))
    drawCircle(
        color = OperatorColors.Phosphor.copy(alpha = 0.05f + 0.05f * breath),
        radius = sphereRadius * (0.5f + 0.04f * breath),
        center = center,
    )
    drawCircle(
        color = OperatorColors.Phosphor.copy(alpha = 0.10f + 0.08f * breath),
        radius = sphereRadius * 0.22f,
        center = center,
    )
}

private fun DrawScope.drawPulse(
    center: Offset,
    sphereRadius: Float,
    t: Float,
    activity: Float,
    energy: Float,
) {
    // Más actividad / voz, pulsos más frecuentes e intensos: de uno cada 3.6s a ~1s.
    val period = 3.6f - 2.2f * activity - 0.8f * energy
    val progress = (t / period) % 1f
    drawCircle(
        color = OperatorColors.Phosphor.copy(alpha = (1f - progress) * (0.22f + 0.4f * energy)),
        radius = sphereRadius * (0.2f + 1.0f * progress),
        center = center,
        style = Stroke(width = (1.5f + energy).dp.toPx()),
    )
}

private fun DrawScope.drawBurst(center: Offset, sphereRadius: Float, sinceBurst: Float) {
    val duration = 0.9f
    if (sinceBurst < 0f || sinceBurst > duration) return
    val progress = sinceBurst / duration
    drawCircle(
        color = OperatorColors.Cyan.copy(alpha = (1f - progress) * 0.5f),
        radius = sphereRadius * 1.25f * progress,
        center = center,
        style = Stroke(width = 3.dp.toPx() * (1f - progress) + 1.dp.toPx()),
    )
}
