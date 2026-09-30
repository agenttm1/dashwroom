package com.dashwroom.f1telemetry.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dashwroom.f1telemetry.core.model.RaceState
import com.dashwroom.f1telemetry.core.model.TrackOutline
import com.dashwroom.f1telemetry.core.protocol.PacketFormat
import com.dashwroom.f1telemetry.core.state.HotTelemetry
import com.dashwroom.f1telemetry.ui.theme.TeamColors
import kotlin.math.min

/** Per-vehicle-index dot colours plus who to emphasise. */
@Immutable
class MapCars(val colors: List<Color>, val player: Int, val selected: Int = -1) {
    override fun equals(other: Any?) = other is MapCars && other.colors == colors && other.player == player && other.selected == selected

    override fun hashCode() = (colors.hashCode() * 31 + player) * 31 + selected

    companion object {
        fun from(race: RaceState, player: Int, selected: Int = -1): MapCars {
            val colors = MutableList(PacketFormat.MAX_CARS) { Color.Gray }
            for (d in race.drivers) colors[d.vehicleIndex] = TeamColors.of(d.teamId, d.liveryColour)
            return MapCars(colors, player, selected)
        }
    }
}

/**
 * Circuit map auto-scaled from the outline learned out of Motion data, with every car drawn at its
 * world position. [frame] should tick every display frame: positions are interpolated between the
 * last two 60 Hz motion samples so 90/120 Hz panels move smoothly (one packet interval behind).
 */
@Composable
fun TrackMap(
    outline: TrackOutline?,
    hot: HotTelemetry,
    frame: State<Long>,
    cars: MapCars,
    modifier: Modifier = Modifier,
) {
    val trackColor = MaterialTheme.colorScheme.outline
    val startColor = MaterialTheme.colorScheme.onSurface
    val ring = MaterialTheme.colorScheme.onSurface
    if (outline == null || outline.coverage < 0.05f) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(
                "Track map appears after the first few hundred metres",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    Box(
        modifier
            .semantics { contentDescription = "Track map with car positions" }
            .drawWithCache {
                val pad = 12.dp.toPx()
                val spanX = (outline.maxX - outline.minX).coerceAtLeast(1f)
                val spanZ = (outline.maxZ - outline.minZ).coerceAtLeast(1f)
                val scale = min((size.width - pad * 2) / spanX, (size.height - pad * 2) / spanZ)
                val ox = (size.width - spanX * scale) / 2f - outline.minX * scale
                val oz = (size.height - spanZ * scale) / 2f - outline.minZ * scale
                val path = Path()
                var started = false
                for (i in 0 until outline.bins) {
                    if (!outline.filled[i]) {
                        started = false
                        continue
                    }
                    val px = outline.x[i] * scale + ox
                    val pz = outline.z[i] * scale + oz
                    if (!started) path.moveTo(px, pz) else path.lineTo(px, pz)
                    started = true
                }
                if (outline.coverage > 0.97f && outline.filled[0]) path.close()
                val stroke = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                val dot = 4.5.dp.toPx()
                val bigDot = 7.dp.toPx()
                val ringW = 2.dp.toPx()
                val startBin = if (outline.filled[0]) 0 else -1
                onDrawBehind {
                    drawPath(path, trackColor, style = stroke)
                    if (startBin == 0) {
                        drawCircle(startColor, radius = 3.dp.toPx(), center = Offset(outline.x[0] * scale + ox, outline.z[0] * scale + oz))
                    }
                    // Draw-phase read: only this lambda re-runs per frame.
                    val now = frame.value
                    val cur = hot.motionCurrentNanos
                    val prev = hot.motionPreviousNanos
                    val interval = cur - prev
                    val alpha = if (prev == 0L || interval <= 0L || interval > 200_000_000L) 1f
                    else ((now - cur).toFloat() / interval).coerceIn(0f, 1f)
                    for (i in 0 until PacketFormat.MAX_CARS) {
                        if (!hot.isCarActive(i) || i == cars.player || i == cars.selected) continue
                        val x = lerp(hot.previousCarX(i), hot.carX(i), alpha)
                        val z = lerp(hot.previousCarZ(i), hot.carZ(i), alpha)
                        drawCircle(cars.colors[i], radius = dot, center = Offset(x * scale + ox, z * scale + oz))
                    }
                    // Selected and player cars last, on top, with a ring.
                    for (pass in 0..1) {
                        val i = if (pass == 0) cars.selected else cars.player
                        if (i < 0 || !hot.isCarActive(i)) continue
                        val c = Offset(lerp(hot.previousCarX(i), hot.carX(i), alpha) * scale + ox, lerp(hot.previousCarZ(i), hot.carZ(i), alpha) * scale + oz)
                        drawCircle(ring, radius = bigDot + ringW, center = c)
                        drawCircle(cars.colors[i], radius = bigDot, center = c)
                    }
                }
            }
            .fillMaxSize(),
    )
}

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
