package com.dashwroom.f1telemetry.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dashwroom.f1telemetry.core.model.TyreStintRecord
import com.dashwroom.f1telemetry.ui.theme.DashTheme
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap

/**
 * Position-by-lap chart: every car as a faint line, [highlight] cars drawn on top in their colour.
 * P1 at the top. Cold data (≤ 1 Hz), so plain recomposition is fine here.
 */
@Composable
fun LapPositionChart(
    positions: ImmutableMap<Int, ImmutableList<Int>>,
    highlight: List<Pair<Int, Color>>,
    numCars: Int,
    modifier: Modifier = Modifier,
) {
    val grid = MaterialTheme.colorScheme.outlineVariant
    val faint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 10.sp, color = labelColor)
    val laps = positions.values.maxOfOrNull { it.size } ?: 0
    val cars = numCars.coerceAtLeast(positions.values.maxOfOrNull { l -> l.maxOrNull() ?: 0 } ?: 0).coerceAtLeast(2)
    Canvas(modifier.semantics { contentDescription = "Positions by lap chart" }) {
        val left = 24.dp.toPx()
        val w = size.width - left
        val h = size.height - 14.dp.toPx()
        if (laps < 2) return@Canvas
        fun x(lap: Int) = left + w * lap / (laps - 1).toFloat()
        fun y(pos: Int) = h * (pos - 1) / (cars - 1).toFloat()
        for (p in listOf(1, 5, 10, 15, 20).filter { it <= cars }) {
            drawLine(grid, Offset(left, y(p)), Offset(size.width, y(p)))
            drawText(measurer, "P$p", Offset(0f, y(p) - 6.dp.toPx()), labelStyle)
        }
        drawText(measurer, "Lap 1", Offset(left, h + 1.dp.toPx()), labelStyle)
        drawText(measurer, "$laps", Offset(size.width - 14.dp.toPx(), h + 1.dp.toPx()), labelStyle)
        val highlighted = highlight.map { it.first }.toSet()
        val thin = Stroke(1.dp.toPx())
        val thick = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun pathFor(list: List<Int>): Path = Path().apply {
            var started = false
            list.forEachIndexed { lap, pos ->
                if (pos <= 0) return@forEachIndexed
                if (!started) moveTo(x(lap), y(pos)) else lineTo(x(lap), y(pos))
                started = true
            }
        }
        for ((vehicle, list) in positions) if (vehicle !in highlighted) drawPath(pathFor(list), faint, style = thin)
        for ((vehicle, color) in highlight) positions[vehicle]?.let { drawPath(pathFor(it), color, style = thick) }
    }
}

/** Stints as coloured segments over the race distance. */
@Composable
fun StintBar(stints: List<TyreStintRecord>, currentLap: Int, totalLaps: Int, modifier: Modifier = Modifier) {
    val colors = DashTheme.colors
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val measurer = rememberTextMeasurer()
    val style = TextStyle(fontSize = 11.sp, color = Color.Black)
    val total = maxOf(totalLaps, currentLap, stints.mapNotNull { it.endLap }.maxOrNull() ?: 0, 1)
    val desc = stints.joinToString { s -> "${compoundName(s.visualCompound)} until lap ${s.endLap ?: currentLap}" }
    Row(modifier.fillMaxWidth().height(22.dp).semantics { contentDescription = "Tyre stints: $desc" }) {
        Canvas(Modifier.fillMaxWidth().height(22.dp)) {
            drawRoundRect(track, cornerRadius = CornerRadius(6.dp.toPx()))
            var start = 0
            for (s in stints) {
                val end = s.endLap ?: currentLap
                if (end <= start) continue
                val x0 = size.width * start / total
                val x1 = size.width * end / total
                drawRoundRect(colors.tyre(s.visualCompound), Offset(x0 + 1, 0f), Size(x1 - x0 - 2, size.height), CornerRadius(6.dp.toPx()))
                val label = "${com.dashwroom.f1telemetry.core.spec.VisualCompound.fromId(s.visualCompound).shortLabel} ${end - start}"
                val layout = measurer.measure(label, style)
                if (layout.size.width < x1 - x0 - 4) drawText(layout, topLeft = Offset(x0 + 5.dp.toPx(), (size.height - layout.size.height) / 2f))
                start = end
            }
        }
    }
}
