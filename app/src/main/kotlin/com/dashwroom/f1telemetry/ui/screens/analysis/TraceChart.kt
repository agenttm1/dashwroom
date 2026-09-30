package com.dashwroom.f1telemetry.ui.screens.analysis

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.outlined.ZoomOut
import androidx.compose.material.icons.outlined.ZoomOutMap
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dashwroom.f1telemetry.core.model.LapTrace
import com.dashwroom.f1telemetry.data.settings.SpeedUnit
import com.dashwroom.f1telemetry.ui.format.Fmt
import com.dashwroom.f1telemetry.ui.format.LocalDisplayPrefs
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

val LapAColor = Color(0xFF00C2B2)
val LapBColor = Color(0xFFFF8A00)

/** The visible window as fractions of the lap, plus the scrub position (−1 = none). */
class ChartViewport {
    val start: MutableFloatState = mutableFloatStateOf(0f)
    val end: MutableFloatState = mutableFloatStateOf(1f)
    val scrub: MutableFloatState = mutableFloatStateOf(-1f)

    fun zoom(factor: Float, anchor: Float = 0.5f) {
        val s = start.floatValue
        val e = end.floatValue
        val span = ((e - s) / factor).coerceIn(MIN_SPAN, 1f)
        val center = s + (e - s) * anchor
        var ns = center - span * anchor
        ns = ns.coerceIn(0f, 1f - span)
        start.floatValue = ns
        end.floatValue = ns + span
    }

    fun pan(fraction: Float) {
        val span = end.floatValue - start.floatValue
        val ns = (start.floatValue + fraction * span).coerceIn(0f, 1f - span)
        start.floatValue = ns
        end.floatValue = ns + span
    }

    fun reset() {
        start.floatValue = 0f
        end.floatValue = 1f
    }

    companion object {
        const val MIN_SPAN = 0.02f
    }
}

private enum class Channel(val label: String, val weight: Float) {
    SPEED("Speed", 2f), THROTTLE("Throttle", 1f), BRAKE("Brake", 1f), GEAR("Gear", 1f), STEER("Steering", 1f), DELTA("Δ time", 1.3f)
}

/**
 * Stacked telemetry traces over lap distance: speed, throttle, brake, gear, steering, and — when
 * two laps are overlaid — the running time delta. One finger scrubs a crosshair with a readout;
 * two fingers pinch-zoom and pan. Paths are rebuilt only when the zoom window or size changes;
 * scrubbing just redraws.
 */
@Composable
fun TraceChart(a: LapTrace?, b: LapTrace?, modifier: Modifier = Modifier, viewport: ChartViewport = remember { ChartViewport() }) {
    val prefs = LocalDisplayPrefs.current
    val mph = prefs.speed == SpeedUnit.MPH
    val grid = MaterialTheme.colorScheme.outlineVariant
    val label = MaterialTheme.colorScheme.onSurfaceVariant
    val crosshair = MaterialTheme.colorScheme.onSurface
    val labelBg = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.85f)
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 11.sp, color = label)
    val channels = Channel.entries.filter { it != Channel.DELTA || b != null }
    if (a == null) {
        Column(modifier, verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Pick a lap with telemetry to see its traces", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    Column(modifier) {
        Readout(a, b, viewport, mph)
        Spacer(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .heightIn(min = 240.dp)
                .semantics {
                    contentDescription = "Telemetry traces for lap ${a.lapNumber}" + (b?.let { " compared with lap ${it.lapNumber}" } ?: "") +
                        ". Drag to scrub, pinch to zoom."
                }
                .pointerInput(a, b) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        viewport.scrub.floatValue = toFraction(down.position.x, size.width.toFloat(), viewport)
                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.count { it.pressed }
                            if (pressed == 0) break
                            if (pressed >= 2) {
                                val zoom = event.calculateZoom()
                                val pan = event.calculatePan()
                                val centroid = event.calculateCentroid()
                                if (zoom != 1f) viewport.zoom(zoom, (centroid.x / size.width).coerceIn(0f, 1f))
                                if (pan.x != 0f) viewport.pan(-pan.x / size.width)
                            } else {
                                val p = event.changes.first { it.pressed }
                                viewport.scrub.floatValue = toFraction(p.position.x, size.width.toFloat(), viewport)
                            }
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    }
                }
                .drawWithCache {
                    val s = viewport.start.floatValue
                    val e = viewport.end.floatValue
                    val totalWeight = channels.sumOf { it.weight.toDouble() }.toFloat()
                    val gap = 6.dp.toPx()
                    val usable = size.height - gap * (channels.size - 1)
                    val bands = ArrayList<Pair<Channel, ClosedFloatingPointRange<Float>>>()
                    var y = 0f
                    for (c in channels) {
                        val h = usable * c.weight / totalWeight
                        bands += c to (y..y + h)
                        y += h + gap
                    }
                    val maxSpeed = max(a.speedKph.max(), b?.speedKph?.max() ?: 0f).coerceAtLeast(100f)
                    val maxDelta = if (b != null) deltaRange(a, b) else 1f
                    fun range(c: Channel): Pair<Float, Float> = when (c) {
                        Channel.SPEED -> 0f to maxSpeed
                        Channel.THROTTLE, Channel.BRAKE -> 0f to 1f
                        Channel.GEAR -> 0f to 8f
                        Channel.STEER -> -1f to 1f
                        Channel.DELTA -> -maxDelta to maxDelta
                    }
                    fun values(t: LapTrace, c: Channel): FloatArray = when (c) {
                        Channel.SPEED -> t.speedKph
                        Channel.THROTTLE -> t.throttle
                        Channel.BRAKE -> t.brake
                        Channel.GEAR -> t.gear
                        Channel.STEER -> t.steer
                        Channel.DELTA -> t.time
                    }
                    fun path(t: LapTrace, c: Channel, band: ClosedFloatingPointRange<Float>, deltaAgainst: LapTrace? = null): Path {
                        val (lo, hi) = range(c)
                        val n = t.bins
                        val first = (s * (n - 1)).toInt().coerceIn(0, n - 1)
                        val last = (e * (n - 1)).roundToInt().coerceIn(first, n - 1)
                        val step = max(1, (last - first) / max(1, size.width.toInt()))
                        val src = values(t, c)
                        val p = Path()
                        var i = first
                        var started = false
                        while (i <= last) {
                            val v = if (deltaAgainst != null) src[i] - deltaAgainst.time[(i * deltaAgainst.bins / n).coerceAtMost(deltaAgainst.bins - 1)] else src[i]
                            val x = ((i / (n - 1f)) - s) / (e - s) * size.width
                            val yy = band.endInclusive - ((v - lo) / (hi - lo)).coerceIn(0f, 1f) * (band.endInclusive - band.start)
                            if (!started) p.moveTo(x, yy) else p.lineTo(x, yy)
                            started = true
                            i += step
                        }
                        return p
                    }
                    val stroke = Stroke(2.dp.toPx(), join = StrokeJoin.Round)
                    val pathsA = bands.map { (c, band) -> if (c == Channel.DELTA) null else path(a, c, band) }
                    val pathsB = bands.map { (c, band) -> if (b == null) null else if (c == Channel.DELTA) path(b, c, band, deltaAgainst = a) else path(b, c, band) }
                    val sectorXs = sectorBoundaries(a).map { ((it - s) / (e - s)) * size.width }.filter { it in 0f..size.width }
                    val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx()))
                    val labels = bands.map { (c, band) -> measurer.measure(c.label, labelStyle) to band }
                    onDrawBehind {
                        for ((c, band) in bands) {
                            drawLine(grid, Offset(0f, band.endInclusive), Offset(size.width, band.endInclusive))
                            if (c == Channel.DELTA || c == Channel.STEER) {
                                val mid = (band.start + band.endInclusive) / 2f
                                drawLine(grid, Offset(0f, mid), Offset(size.width, mid))
                            }
                        }
                        for (x in sectorXs) drawLine(grid, Offset(x, 0f), Offset(x, size.height), pathEffect = dash)
                        for (i in bands.indices) {
                            pathsB[i]?.let { drawPath(it, LapBColor, style = stroke) }
                            pathsA[i]?.let { drawPath(it, LapAColor, style = stroke) }
                        }
                        for ((layout, band) in labels) {
                            drawRoundRect(
                                labelBg,
                                topLeft = Offset(2.dp.toPx(), band.start),
                                size = androidx.compose.ui.geometry.Size(layout.size.width + 6.dp.toPx(), layout.size.height.toFloat()),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()),
                            )
                            drawText(layout, topLeft = Offset(5.dp.toPx(), band.start))
                        }
                        // Draw-phase read: scrubbing only redraws, the cached paths stay.
                        val f = viewport.scrub.floatValue
                        if (f in s..e) {
                            val x = (f - s) / (e - s) * size.width
                            drawLine(crosshair, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.5.dp.toPx())
                        }
                    }
                },
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "0 m",
                style = MaterialTheme.typography.bodySmall,
                color = label,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { viewport.zoom(1f / 1.6f) }) { Icon(Icons.Outlined.ZoomOut, contentDescription = "Zoom out") }
            IconButton(onClick = { viewport.reset() }) { Icon(Icons.Outlined.ZoomOutMap, contentDescription = "Show whole lap") }
            IconButton(onClick = { viewport.zoom(1.6f) }) { Icon(Icons.Outlined.ZoomIn, contentDescription = "Zoom in") }
            Text(
                "${a.trackLengthM.roundToInt()} m",
                style = MaterialTheme.typography.bodySmall,
                color = label,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
            )
        }
    }
}

private fun toFraction(x: Float, width: Float, v: ChartViewport): Float {
    val s = v.start.floatValue
    val e = v.end.floatValue
    return (s + (x / width).coerceIn(0f, 1f) * (e - s))
}

private fun deltaRange(a: LapTrace, b: LapTrace): Float {
    var m = 0.2f
    for (i in 0 until a.bins) {
        val j = (i * b.bins / a.bins).coerceAtMost(b.bins - 1)
        m = max(m, abs(b.time[j] - a.time[i]))
    }
    return m * 1.1f
}

/** Lap fractions where S2 and S3 begin, from the elapsed-time channel. */
private fun sectorBoundaries(t: LapTrace): List<Float> {
    val out = ArrayList<Float>(2)
    val s1 = t.sectorsMs.s1Ms / 1000f
    val s2 = (t.sectorsMs.s1Ms + t.sectorsMs.s2Ms) / 1000f
    for (target in floatArrayOf(s1, s2)) {
        if (target <= 0f) continue
        val i = t.time.indexOfFirst { it >= target }
        if (i > 0) out += i / (t.bins - 1f)
    }
    return out
}

/** Values under the crosshair for both laps. Recomposes only while scrubbing. */
@Composable
private fun Readout(a: LapTrace, b: LapTrace?, viewport: ChartViewport, mph: Boolean) {
    val f by viewport.scrub
    Column(Modifier.fillMaxWidth().padding(bottom = 4.dp).heightIn(min = 48.dp)) {
        if (f < 0f) {
            Text(
                "Drag across the chart to read values · pinch to zoom",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LegendRow(a, b)
            return@Column
        }
        val i = (f * (a.bins - 1)).roundToInt().coerceIn(0, a.bins - 1)
        Text("${(a.distanceAt(i)).roundToInt()} m", style = MaterialTheme.typography.labelLarge)
        ReadoutLine("L${a.lapNumber}", a, i, mph, LapAColor)
        if (b != null) {
            val j = (f * (b.bins - 1)).roundToInt().coerceIn(0, b.bins - 1)
            ReadoutLine("L${b.lapNumber}", b, j, mph, LapBColor)
            val d = ((b.time[j] - a.time[i]) * 1000).roundToInt()
            Text("L${b.lapNumber} vs L${a.lapNumber}: ${Fmt.delta(d)} s", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ReadoutLine(name: String, t: LapTrace, i: Int, mph: Boolean, color: Color) {
    val speed = if (mph) t.speedKph[i] * 0.621371f else t.speedKph[i]
    Text(
        String.format(
            Locale.US, "%s  %d %s · throttle %d%% · brake %d%% · gear %d · steer %+d%%",
            name, speed.roundToInt(), if (mph) "mph" else "km/h", (t.throttle[i] * 100).roundToInt(), (t.brake[i] * 100).roundToInt(),
            t.gear[i].roundToInt(), (t.steer[i] * 100).roundToInt(),
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = color,
    )
}

@Composable
private fun LegendRow(a: LapTrace, b: LapTrace?) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("■ Lap ${a.lapNumber} ${Fmt.lapTime(a.lapTimeMs)}", color = LapAColor, style = MaterialTheme.typography.labelLarge)
        if (b != null) Text("■ Lap ${b.lapNumber} ${Fmt.lapTime(b.lapTimeMs)}", color = LapBColor, style = MaterialTheme.typography.labelLarge)
    }
}
