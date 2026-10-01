package com.dashwroom.f1telemetry.ui.screens.drive

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dashwroom.f1telemetry.core.state.HotTelemetry
import com.dashwroom.f1telemetry.data.settings.SpeedUnit
import com.dashwroom.f1telemetry.ui.format.LocalDisplayPrefs
import com.dashwroom.f1telemetry.ui.hot.GlyphAtlas
import com.dashwroom.f1telemetry.ui.hot.GlyphBuffer
import com.dashwroom.f1telemetry.ui.hot.drawRevLeds
import com.dashwroom.f1telemetry.ui.hot.numberStyle
import com.dashwroom.f1telemetry.ui.hot.rememberGlyphAtlas
import com.dashwroom.f1telemetry.ui.theme.DashTheme
import kotlin.math.roundToInt

/*
 * Drive-screen widgets. Same contract as the other hot widgets: the frame state is read only in
 * draw lambdas, text is drawn from pre-measured glyphs, nothing allocates per frame. Sizes follow
 * the space each widget is given, so they scale from a phone to a 13" tablet.
 */

/** Full-width rev LEDs. */
@Composable
fun HotRevBar(hot: HotTelemetry, frame: State<Long>, modifier: Modifier = Modifier) {
    val colors = DashTheme.colors
    val off = MaterialTheme.colorScheme.surfaceContainerHighest
    Canvas(modifier.clearAndSetSemantics { contentDescription = "Rev lights" }) {
        frame.value
        drawRevLeds(hot, colors, off, 0f, 0f, size.width, size.height)
    }
}

/**
 * The gear, as large as the space allows. It turns purple at the top of the rev range (shift
 * now) and shows the game's suggested gear underneath when it differs.
 */
@Composable
fun HotGear(hot: HotTelemetry, frame: State<Long>, modifier: Modifier = Modifier) {
    val colors = DashTheme.colors
    val onSurface = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    BoxWithConstraints(modifier.clearAndSetSemantics { contentDescription = "Gear" }) {
        val density = LocalDensity.current
        val bigSize = with(density) { (minOf(maxHeight * 0.8f, maxWidth * 0.9f)).toSp() }
        val smallSize = with(density) { (minOf(maxHeight, maxWidth) * 0.14f).toSp() }
        val big = rememberGlyphAtlas(numberStyle.copy(fontSize = bigSize, fontWeight = FontWeight.Bold))
        val small = rememberGlyphAtlas(numberStyle.copy(fontSize = smallSize, fontWeight = FontWeight.Bold), GlyphAtlas.DEFAULT_GLYPHS + "▲▼")
        val buf = remember { GlyphBuffer() }
        Canvas(Modifier.fillMaxSize()) {
            frame.value
            buf.clear()
            when (val g = hot.gear) {
                -1 -> buf.append('R')
                0 -> buf.append('N')
                else -> buf.appendInt(g)
            }
            val shift = hot.revLightsPercent >= 92
            big.drawCentered(this, buf, size.width / 2f, (size.height - big.height) / 2f - small.height * 0.3f, if (shift) colors.sessionBest else onSurface)
            val s = hot.suggestedGear
            if (s > 0 && s != hot.gear) {
                buf.clear().append(if (s > hot.gear) '▲' else '▼').appendInt(s)
                small.drawCentered(this, buf, size.width / 2f, size.height - small.height, muted)
            }
        }
    }
}

/** Speed in the chosen unit with the unit beneath. */
@Composable
fun HotSpeedBig(hot: HotTelemetry, frame: State<Long>, modifier: Modifier = Modifier) {
    val prefs = LocalDisplayPrefs.current
    val mph = prefs.speed == SpeedUnit.MPH
    val onSurface = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier.clearAndSetSemantics { contentDescription = "Speed" }) {
        val density = LocalDensity.current
        val size = with(density) { minOf(maxHeight * 0.62f, maxWidth * 0.32f).toSp() }
        val atlas = rememberGlyphAtlas(numberStyle.copy(fontSize = size, fontWeight = FontWeight.SemiBold))
        val unitStyle = TextStyle(fontSize = with(density) { (maxHeight * 0.16f).toSp() }, color = muted)
        val unit = remember(mph, unitStyle) { measurer.measure(if (mph) "mph" else "km/h", unitStyle) }
        val buf = remember { GlyphBuffer() }
        Canvas(Modifier.fillMaxSize()) {
            frame.value
            val v = if (mph) (hot.speedKph * 0.621371f).roundToInt() else hot.speedKph
            buf.clear().appendInt(v)
            val top = (this.size.height - atlas.height - unit.size.height) / 2f
            atlas.drawCentered(this, buf, this.size.width / 2f, top, onSurface)
            drawText(unit, topLeft = Offset((this.size.width - unit.size.width) / 2f, top + atlas.height))
        }
    }
}

/** Clutch, brake and throttle as vertical bars with their percentage on top. */
@Composable
fun HotPedals(hot: HotTelemetry, frame: State<Long>, modifier: Modifier = Modifier) {
    val colors = DashTheme.colors
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val onSurface = MaterialTheme.colorScheme.onSurface
    val clutchColor = Color(0xFF2F7BFF)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val atlas = rememberGlyphAtlas(numberStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold))
    val buf = remember { GlyphBuffer() }
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = muted)
    val labels = remember(labelStyle) { listOf("CLU", "BRAKE", "THROTTLE").map { measurer.measure(it, labelStyle) } }
    Canvas(modifier.clearAndSetSemantics { contentDescription = "Pedal inputs: clutch, brake and throttle" }) {
        frame.value
        val textH = atlas.height
        val labelH = labels[0].size.height.toFloat()
        val barTop = textH + 4.dp.toPx()
        val barH = size.height - barTop - labelH - 4.dp.toPx()
        val gap = size.width * 0.06f
        // Clutch is narrow; brake and throttle share the rest.
        val clutchW = size.width * 0.16f
        val mainW = (size.width - clutchW - gap * 2) / 2f
        val r = CornerRadius(6.dp.toPx())
        fun bar(left: Float, width: Float, value: Float, color: Color) {
            drawRoundRect(track, Offset(left, barTop), Size(width, barH), r)
            val h = barH * value.coerceIn(0f, 1f)
            if (h > 0f) drawRoundRect(color, Offset(left, barTop + barH - h), Size(width, h), r)
            buf.clear().appendInt((value * 100).roundToInt().coerceIn(0, 100))
            atlas.drawCentered(this, buf, left + width / 2f, 0f, onSurface)
        }
        fun label(i: Int, left: Float, width: Float) {
            val l = labels[i]
            if (l.size.width <= width + gap) drawText(l, topLeft = Offset(left + (width - l.size.width) / 2f, size.height - labelH))
        }
        val brakeLeft = clutchW + gap
        val throttleLeft = clutchW + gap * 2 + mainW
        bar(0f, clutchW, hot.clutch / 100f, clutchColor)
        bar(brakeLeft, mainW, hot.brake, colors.danger)
        bar(throttleLeft, mainW, hot.throttle, colors.personalBest)
        label(0, 0f, clutchW)
        label(1, brakeLeft, mainW)
        label(2, throttleLeft, mainW)
    }
}

/** Steering input: a centred bar growing left or right. */
@Composable
fun HotSteering(hot: HotTelemetry, frame: State<Long>, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val fill = MaterialTheme.colorScheme.secondary
    val mark = MaterialTheme.colorScheme.onSurface
    Canvas(modifier.clearAndSetSemantics { contentDescription = "Steering input" }) {
        frame.value
        val r = CornerRadius(size.height / 2f)
        drawRoundRect(track, cornerRadius = r)
        val mid = size.width / 2f
        val w = hot.steer.coerceIn(-1f, 1f) * mid
        val left = if (w < 0) mid + w else mid
        drawRoundRect(fill, Offset(left, 0f), Size(kotlin.math.abs(w), size.height), r)
        drawLine(mark, Offset(mid, 0f), Offset(mid, size.height), strokeWidth = 2.dp.toPx())
    }
}

/**
 * The last few seconds of throttle (green) and brake (red), newest on the right. Samples are
 * taken once per new telemetry frame into a fixed ring; the path object is reused.
 */
@Composable
fun HotPedalTrace(hot: HotTelemetry, frame: State<Long>, modifier: Modifier = Modifier, samples: Int = 240) {
    val colors = DashTheme.colors
    val grid = MaterialTheme.colorScheme.outlineVariant
    val ring = remember(samples) { PedalRing(samples) }
    val throttlePath = remember { Path() }
    val brakePath = remember { Path() }
    Canvas(modifier.clearAndSetSemantics { contentDescription = "Recent throttle and brake trace" }) {
        frame.value
        ring.sample(hot)
        val stroke = Stroke(2.5.dp.toPx(), join = StrokeJoin.Round)
        drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height))
        drawLine(grid, Offset(0f, 0f), Offset(size.width, 0f))
        ring.fill(throttlePath, size.width, size.height, throttle = true)
        ring.fill(brakePath, size.width, size.height, throttle = false)
        drawPath(brakePath, colors.danger, style = stroke)
        drawPath(throttlePath, colors.personalBest, style = stroke)
    }
}

private class PedalRing(private val capacity: Int) {
    private val throttle = FloatArray(capacity)
    private val brake = FloatArray(capacity)
    private var head = 0
    private var count = 0
    private var lastVersion = -1L

    fun sample(hot: HotTelemetry) {
        val v = hot.version
        if (v == lastVersion) return
        lastVersion = v
        throttle[head] = hot.throttle
        brake[head] = hot.brake
        head = (head + 1) % capacity
        if (count < capacity) count++
    }

    fun fill(path: Path, width: Float, height: Float, throttle: Boolean) {
        path.reset()
        if (count < 2) return
        val src = if (throttle) this.throttle else brake
        val step = width / (capacity - 1)
        val start = (head - count + capacity) % capacity
        for (i in 0 until count) {
            val v = src[(start + i) % capacity]
            val x = width - (count - 1 - i) * step
            val y = height - v.coerceIn(0f, 1f) * height
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
    }
}

/** DRS / lap-invalid / shift lights as pills; only those that apply are drawn. */
@Composable
fun HotFlags(hot: HotTelemetry, frame: State<Long>, modifier: Modifier = Modifier) {
    val colors = DashTheme.colors
    val muted = MaterialTheme.colorScheme.surfaceContainerHighest
    val measurer = rememberTextMeasurer()
    val style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.Black)
    val drs = remember(style) { measurer.measure("DRS", style) }
    val drsReady = remember(style) { measurer.measure("DRS READY", style.copy(color = colors.drs)) }
    val invalid = remember(style) { measurer.measure("LAP INVALID", style.copy(color = Color.White)) }
    val ot = remember(style) { measurer.measure("OVERTAKE", style) }
    Canvas(modifier.clearAndSetSemantics { contentDescription = "DRS and lap status" }) {
        frame.value
        val pad = 10.dp.toPx()
        val r = CornerRadius(size.height / 2f)
        // Returns the next x; no captured mutable state, so nothing is allocated per frame.
        fun pill(x: Float, layout: androidx.compose.ui.text.TextLayoutResult, bg: Color, outline: Color?): Float {
            val w = layout.size.width + pad * 2
            drawRoundRect(bg, Offset(x, 0f), Size(w, size.height), r)
            if (outline != null) drawRoundRect(outline, Offset(x, 0f), Size(w, size.height), r, style = Stroke(2.dp.toPx()))
            drawText(layout, topLeft = Offset(x + pad, (size.height - layout.size.height) / 2f))
            return x + w + pad
        }
        var x = 0f
        if (hot.drsOpen) x = pill(x, drs, colors.drs, null) else if (hot.drsAllowed) x = pill(x, drsReady, muted, colors.drs)
        if (hot.overtakeActive) x = pill(x, ot, colors.sessionBest, null)
        if (hot.currentLapInvalid) pill(x, invalid, colors.danger, null)
    }
}
