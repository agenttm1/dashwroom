package com.dashwroom.f1telemetry.ui.hot

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dashwroom.f1telemetry.core.model.PlayerCarState
import com.dashwroom.f1telemetry.core.state.HotTelemetry
import com.dashwroom.f1telemetry.data.settings.DeltaReference
import com.dashwroom.f1telemetry.data.settings.SpeedUnit
import com.dashwroom.f1telemetry.ui.format.LocalDisplayPrefs
import com.dashwroom.f1telemetry.ui.theme.DashColors
import com.dashwroom.f1telemetry.ui.theme.DashTheme
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * Widgets fed straight from HotTelemetry. Every one follows the same rule: the frame state is read
 * only inside the draw lambda, text goes through a GlyphAtlas, and nothing in the draw path
 * allocates — so they update at the display's full rate without recomposing anything.
 */

fun HotTelemetry.delta(reference: DeltaReference): Int = when (reference) {
    DeltaReference.PERSONAL_BEST -> deltaToPersonalBestMs
    DeltaReference.SESSION_BEST -> deltaToSessionBestMs
    DeltaReference.LAST_LAP -> deltaToLastLapMs
}

internal val numberStyle = TextStyle(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum")

/**
 * Horizontal delta bar: centre = level with the reference, left/green = ahead, right/red = behind,
 * full scale ±[rangeMs]. The signed value is drawn over the bar.
 */
@Composable
fun HotDeltaBar(
    hot: HotTelemetry,
    frame: State<Long>,
    reference: DeltaReference,
    modifier: Modifier = Modifier,
    rangeMs: Int = 1_000,
    height: Dp = 40.dp,
    fontSize: Int = 20,
) {
    val colors = DashTheme.colors
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val atlas = rememberGlyphAtlas(numberStyle.copy(fontSize = fontSize.sp))
    val buf = remember { GlyphBuffer() }
    Canvas(
        modifier.fillMaxWidth().height(height).clearAndSetSemantics { contentDescription = "Live delta to ${reference.label.lowercase()}" },
    ) {
        frame.value // draw-phase subscription
        val d = hot.delta(reference)
        val r = CornerRadius(size.height / 4f)
        drawRoundRect(track, cornerRadius = r)
        val mid = size.width / 2f
        if (hot.hasDelta(d)) {
            val frac = (d.toFloat() / rangeMs).coerceIn(-1f, 1f)
            val color = if (d <= 0) colors.personalBest else colors.danger
            val w = abs(frac) * mid
            val left = if (frac < 0) mid - w else mid
            drawRoundRect(color.copy(alpha = 0.85f), topLeft = Offset(left, 0f), size = Size(w, size.height), cornerRadius = r)
            drawLine(onSurface.copy(alpha = 0.35f), Offset(mid, 0f), Offset(mid, size.height), strokeWidth = 2.dp.toPx())
            buf.clear().appendDelta(d)
            atlas.drawCentered(this, buf, mid, (size.height - atlas.height) / 2f, onSurface)
        } else {
            buf.clear().append('-').append('.').append('-').append('-').append('-')
            atlas.drawCentered(this, buf, mid, (size.height - atlas.height) / 2f, muted)
        }
    }
}

/** ERS store bar (0–4 MJ) with the charge in MJ. */
@Composable
fun HotErsBar(hot: HotTelemetry, frame: State<Long>, modifier: Modifier = Modifier) {
    val fill = Color(0xFFFFD500)
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val onSurface = MaterialTheme.colorScheme.onSurface
    val atlas = rememberGlyphAtlas(numberStyle.copy(fontSize = 15.sp), glyphs = GlyphAtlas.DEFAULT_GLYPHS + "%")
    val buf = remember { GlyphBuffer() }
    Canvas(modifier.fillMaxWidth().height(28.dp).clearAndSetSemantics { contentDescription = "ERS store" }) {
        frame.value
        val frac = (hot.ersStoreEnergy / PlayerCarState.ERS_MAX_J).coerceIn(0f, 1f)
        val r = CornerRadius(size.height / 4f)
        drawRoundRect(track, cornerRadius = r)
        drawRoundRect(fill, size = Size(size.width * frac, size.height), cornerRadius = r)
        val pct = (frac * 100).roundToInt()
        buf.clear().appendInt(pct).append('%')
        atlas.drawRightAligned(this, buf, size.width - 6.dp.toPx(), (size.height - atlas.height) / 2f, if (frac > 0.85f) Color.Black else onSurface)
    }
}

/** A single hot number (e.g. speed), right-aligned, in the given unit. */
@Composable
fun HotSpeed(hot: HotTelemetry, frame: State<Long>, modifier: Modifier = Modifier, fontSize: Int = 34) {
    val prefs = LocalDisplayPrefs.current
    val onSurface = MaterialTheme.colorScheme.onSurface
    val atlas = rememberGlyphAtlas(numberStyle.copy(fontSize = fontSize.sp))
    val buf = remember { GlyphBuffer() }
    val mph = prefs.speed == SpeedUnit.MPH
    Canvas(modifier.clearAndSetSemantics { contentDescription = "Speed" }) {
        frame.value
        val v = if (mph) (hot.speedKph * 0.621371f).roundToInt() else hot.speedKph
        buf.clear().appendInt(v)
        atlas.drawCentered(this, buf, size.width / 2f, (size.height - atlas.height) / 2f, onSurface)
    }
}

/**
 * The Race screen's full-rate strip: rev LEDs, gear, speed, DRS, current lap time and delta.
 * All of it redraws per display frame with zero recomposition.
 */
@Composable
fun RaceTopStrip(
    hot: HotTelemetry,
    frame: State<Long>,
    reference: DeltaReference,
    modifier: Modifier = Modifier,
) {
    val colors = DashTheme.colors
    val prefs = LocalDisplayPrefs.current
    val surface = MaterialTheme.colorScheme.surfaceContainer
    val off = MaterialTheme.colorScheme.surfaceContainerHighest
    val onSurface = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val big = rememberGlyphAtlas(numberStyle.copy(fontSize = 34.sp, fontWeight = FontWeight.Bold))
    val mid = rememberGlyphAtlas(numberStyle.copy(fontSize = 22.sp))
    val small = rememberGlyphAtlas(numberStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold), glyphs = GlyphAtlas.DEFAULT_GLYPHS + "DRSkmhp/ACTIVE")
    val buf = remember { GlyphBuffer(24) }
    val mph = prefs.speed == SpeedUnit.MPH
    Row(
        modifier
            .fillMaxWidth()
            .height(76.dp)
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clearAndSetSemantics { contentDescription = "Live car data: gear, speed, revs, DRS, lap time and delta" },
    ) {
        Canvas(Modifier.weight(1f).fillMaxHeight()) {
            frame.value
            drawRoundRect(surface, cornerRadius = CornerRadius(12.dp.toPx()))
            val pad = 10.dp.toPx()
            // Gear and speed take fixed content-sized cells; the rev LEDs get the rest.
            val gearCx = pad + 26.dp.toPx()
            val speedRight = pad + 52.dp.toPx() + 72.dp.toPx()
            buf.clear()
            when (val g = hot.gear) {
                -1 -> buf.append('R')
                0 -> buf.append('N')
                else -> buf.appendInt(g)
            }
            big.drawCentered(this, buf, gearCx, (size.height - big.height) / 2f, onSurface)
            val v = if (mph) (hot.speedKph * 0.621371f).roundToInt() else hot.speedKph
            buf.clear().appendInt(v)
            mid.drawRightAligned(this, buf, speedRight, size.height * 0.16f, onSurface)
            buf.clear()
            if (mph) buf.append('m').append('p').append('h') else buf.append('k').append('m').append('/').append('h')
            small.drawRightAligned(this, buf, speedRight, size.height * 0.16f + mid.height, muted)
            // Rev LEDs across the rest, lap time + delta under them.
            val ledLeft = speedRight + 20.dp.toPx()
            drawRevLeds(hot, colors, off, ledLeft, pad, size.width - pad - ledLeft, size.height * 0.24f)
            buf.clear().appendLapTime(hot.currentLapTimeMs)
            mid.draw(this, buf, ledLeft, size.height * 0.42f, if (hot.currentLapInvalid) colors.danger else onSurface)
            val d = hot.delta(reference)
            buf.clear()
            if (hot.hasDelta(d)) buf.appendDelta(d) else buf.append('-').append('.').append('-').append('-').append('-')
            mid.drawRightAligned(
                this, buf, size.width - pad, size.height * 0.42f,
                if (!hot.hasDelta(d)) muted else if (d <= 0) colors.personalBest else colors.danger,
            )
        }
        Canvas(Modifier.padding(start = 8.dp).weight(0.16f).fillMaxHeight()) {
            frame.value
            val open = hot.drsOpen
            val allowed = hot.drsAllowed
            val bg = when {
                open -> colors.drs
                allowed -> colors.drs.copy(alpha = 0.25f)
                else -> surface
            }
            drawRoundRect(bg, cornerRadius = CornerRadius(12.dp.toPx()))
            if (allowed && !open) {
                drawRoundRect(colors.drs, cornerRadius = CornerRadius(12.dp.toPx()), style = Stroke(2.dp.toPx()))
            }
            buf.clear().append('D').append('R').append('S')
            small.drawCentered(this, buf, size.width / 2f, (size.height - small.height) / 2f, if (open) Color.Black else if (allowed) onSurface else muted)
        }
    }
}

internal fun DrawScope.drawRevLeds(hot: HotTelemetry, colors: DashColors, off: Color, left: Float, top: Float, width: Float, height: Float) {
    val count = 15
    val gap = width * 0.012f
    val w = (width - gap * (count - 1)) / count
    val bits = hot.revLightsBitValue
    val lit = if (bits != 0) -1 else (hot.revLightsPercent * count / 100)
    val r = CornerRadius(height / 3f)
    for (i in 0 until count) {
        val on = if (lit < 0) (bits ushr i) and 1 == 1 else i < lit
        val color = when {
            !on -> off
            i < 5 -> colors.personalBest
            i < 10 -> colors.danger
            else -> colors.sessionBest
        }
        drawRoundRect(color, topLeft = Offset(left + i * (w + gap), top), size = Size(w, height), cornerRadius = r)
    }
}

/** The player's running lap time; red while the lap is invalid. */
@Composable
fun HotLapTime(hot: HotTelemetry, frame: State<Long>, modifier: Modifier = Modifier, fontSize: Int = 30) {
    val colors = DashTheme.colors
    val onSurface = MaterialTheme.colorScheme.onSurface
    val atlas = rememberGlyphAtlas(numberStyle.copy(fontSize = fontSize.sp))
    val buf = remember { GlyphBuffer() }
    Canvas(modifier.fillMaxWidth().height((fontSize * 1.5f).dp).clearAndSetSemantics { contentDescription = "Current lap time" }) {
        frame.value
        buf.clear().appendLapTime(hot.currentLapTimeMs)
        atlas.draw(this, buf, 0f, (size.height - atlas.height) / 2f, if (hot.currentLapInvalid) colors.danger else onSurface)
    }
}
