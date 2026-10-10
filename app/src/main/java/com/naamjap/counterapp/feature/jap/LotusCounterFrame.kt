package com.naamjap.counterapp.feature.jap

import androidx.compose.foundation.Canvas
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Draws a quiet, symmetrical lotus wreath outside the supplied counter ring. */
@Composable
internal fun LotusCounterFrame(
    ringDiameter: Dp,
    modifier: Modifier = Modifier
) {
    val gold = MaterialTheme.colorScheme.primary
    val paleGold = MaterialTheme.colorScheme.secondary

    Canvas(
        modifier = modifier.drawWithCache {
            val center = Offset(size.width / 2f, size.height / 2f)
            val frameRadius = min(size.width, size.height) / 2f
            val ringRadius = ringDiameter.toPx() / 2f
            val baseRadius = ringRadius + 2.dp.toPx()
            val tipRadius = frameRadius - 2.dp.toPx()
            val petalLength = (tipRadius - baseRadius).coerceAtLeast(0f)
            // Fewer, broader petals read as a layered lotus instead of a dense sunburst.
            val outerCount = 20
            val outerHalfWidth = min(
                petalLength * 0.48f,
                (baseRadius + petalLength * 0.5f) *
                    kotlin.math.sin(Math.PI.toFloat() / outerCount) * 0.78f
            )
            val outer = if (petalLength > 1.dp.toPx()) {
                List(outerCount) { index ->
                    val angle = -Math.PI.toFloat() / 2f +
                        (2f * Math.PI.toFloat() * index / outerCount)
                    makePetal(center, baseRadius, tipRadius, outerHalfWidth, angle)
                }
            } else emptyList()

            val innerCount = 20
            val innerTipRadius = baseRadius + petalLength * 0.68f
            val innerHalfWidth = outerHalfWidth * 0.68f
            val inner = if (petalLength > 1.dp.toPx()) {
                List(innerCount) { index ->
                    val angle = -Math.PI.toFloat() / 2f +
                        (2f * Math.PI.toFloat() * (index + 0.5f) / innerCount)
                    makePetal(center, baseRadius, innerTipRadius, innerHalfWidth, angle)
                }
            } else emptyList()

            onDrawBehind {
                val outline = 0.65.dp.toPx()
                inner.forEach { petal ->
                    drawPath(petal.path, paleGold.copy(alpha = 0.12f))
                    drawPath(petal.path, gold.copy(alpha = 0.24f), style = Stroke(outline))
                    drawPath(petal.vein, gold.copy(alpha = 0.12f), style = Stroke(0.45.dp.toPx()))
                }
                outer.forEach { petal ->
                    drawPath(petal.path, paleGold.copy(alpha = 0.15f))
                    drawPath(petal.path, gold.copy(alpha = 0.32f), style = Stroke(outline))
                    drawPath(petal.vein, gold.copy(alpha = 0.15f), style = Stroke(0.5.dp.toPx()))
                }
            }
        }
    ) { }
}

private data class PetalPaths(val path: Path, val vein: Path)

private fun makePetal(
    center: Offset,
    baseRadius: Float,
    tipRadius: Float,
    halfWidth: Float,
    angle: Float
): PetalPaths {
    val length = (tipRadius - baseRadius).coerceAtLeast(0f)
    fun point(radius: Float, tangent: Float): Offset {
        val c = cos(angle)
        val s = sin(angle)
        return Offset(
            x = center.x + radius * c - tangent * s,
            y = center.y + radius * s + tangent * c
        )
    }

    val path = Path().apply {
        val baseLeft = point(baseRadius, -halfWidth * 0.3f)
        moveTo(baseLeft.x, baseLeft.y)
        cubicTo(
            point(baseRadius + length * 0.22f, -halfWidth * 1.02f).x,
            point(baseRadius + length * 0.22f, -halfWidth * 1.02f).y,
            point(tipRadius - length * 0.25f, -halfWidth * 0.72f).x,
            point(tipRadius - length * 0.25f, -halfWidth * 0.72f).y,
            point(tipRadius, 0f).x,
            point(tipRadius, 0f).y
        )
        cubicTo(
            point(tipRadius - length * 0.25f, halfWidth * 0.72f).x,
            point(tipRadius - length * 0.25f, halfWidth * 0.72f).y,
            point(baseRadius + length * 0.22f, halfWidth * 1.02f).x,
            point(baseRadius + length * 0.22f, halfWidth * 1.02f).y,
            point(baseRadius, halfWidth * 0.3f).x,
            point(baseRadius, halfWidth * 0.3f).y
        )
        close()
    }
    val vein = Path().apply {
        val start = point(baseRadius + length * 0.14f, 0f)
        val end = point(tipRadius - length * 0.18f, 0f)
        moveTo(start.x, start.y)
        cubicTo(
            point(baseRadius + length * 0.42f, 0f).x,
            point(baseRadius + length * 0.42f, 0f).y,
            point(tipRadius - length * 0.34f, 0f).x,
            point(tipRadius - length * 0.34f, 0f).y,
            end.x,
            end.y
        )
    }
    return PetalPaths(path, vein)
}
