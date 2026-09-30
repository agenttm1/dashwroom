package com.dashwroom.f1telemetry.ui.debug

import android.os.Debug
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.dashwroom.f1telemetry.core.model.TelemetryStatus
import com.dashwroom.f1telemetry.core.state.HotTelemetry
import com.dashwroom.f1telemetry.ui.hot.rememberHotFrame
import com.dashwroom.f1telemetry.ui.theme.DashTheme
import com.dashwroom.f1telemetry.ui.theme.MonoNumbers
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt

private data class HudValues(
    val fps: Float = 0f,
    val latencyMs: Float = Float.NaN,
    val janky: Long = 0,
    val frames: Long = 0,
    val allocKbPerSecond: Float = Float.NaN,
)

/** Latency accumulator written from the draw phase; read by the 4 Hz text refresher. */
private class LatencyProbe {
    var sumNanos = 0L
    var samples = 0
}

/**
 * Proof-not-vibes overlay: packets/s, frames/s, receive→draw latency, janky frames, allocation
 * rate. Text refreshes 4×/s; latency is sampled every frame inside a draw lambda, which is the
 * moment the new value actually reaches the screen (plus ~1 frame of display scan-out).
 */
@Composable
fun DebugHud(status: TelemetryStatus, hot: HotTelemetry, modifier: Modifier = Modifier) {
    val window = LocalActivity.current?.window
    val tracker = remember(window) { window?.let(::FrameStatsTracker) }
    DisposableEffect(tracker) {
        tracker?.start()
        onDispose { tracker?.stop() }
    }
    val probe = remember { LatencyProbe() }
    val frame = rememberHotFrame(hot)
    var values by remember { mutableStateOf(HudValues()) }

    LaunchedEffect(tracker) {
        var lastFrames = tracker?.frames?.get() ?: 0L
        var lastTime = System.nanoTime()
        var lastAlloc = allocatedBytes()
        var lastAllocTime = lastTime
        var alloc = Float.NaN
        var tick = 0
        while (true) {
            delay(250)
            val now = System.nanoTime()
            val frames = tracker?.frames?.get() ?: 0L
            val latency = if (probe.samples > 0) probe.sumNanos / probe.samples / 1e6f else Float.NaN
            probe.sumNanos = 0
            probe.samples = 0
            if (++tick % 4 == 0) { // the allocation stat is a string lookup; sample it once a second
                val bytes = allocatedBytes()
                alloc = if (bytes >= 0 && lastAlloc >= 0) (bytes - lastAlloc) / 1024f / ((now - lastAllocTime) / 1e9f) else Float.NaN
                lastAlloc = bytes
                lastAllocTime = now
            }
            values = HudValues(
                fps = (frames - lastFrames) / ((now - lastTime) / 1e9f),
                latencyMs = latency,
                janky = tracker?.jankyFrames?.get() ?: 0,
                frames = frames,
                allocKbPerSecond = alloc,
            )
            lastFrames = frames
            lastTime = now
        }
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color.Black.copy(alpha = 0.78f),
        modifier = modifier
            .padding(8.dp)
            .width(196.dp)
            .clearAndSetSemantics { }
            .drawBehind {
                // Draw-phase read: runs once per frame in which hot data changed.
                if (frame.value == 0L) return@drawBehind
                val received = hot.lastUpdateNanos
                if (received != 0L) {
                    probe.sumNanos += System.nanoTime() - received
                    probe.samples++
                }
            },
    ) {
        val style = MaterialTheme.typography.labelMedium.merge(MonoNumbers)
        val colors = DashTheme.colors
        Column(Modifier.padding(10.dp)) {
            Text("PERF HUD", style = MaterialTheme.typography.labelSmall, color = colors.sessionBest)
            Spacer(Modifier.height(4.dp))
            HudLine("pkt/s", status.packetsPerSecond.roundToInt().toString(), style)
            HudLine("fps", values.fps.roundToInt().toString(), style)
            HudLine(
                "rx→draw",
                if (values.latencyMs.isNaN()) "—" else String.format(Locale.US, "%.1f ms", values.latencyMs),
                style,
                if (values.latencyMs > 20f) colors.warning else null,
            )
            HudLine(
                "janky",
                "${values.janky}/${values.frames}",
                style,
                if (values.janky > 0) colors.warning else colors.connected,
            )
            HudLine(
                "alloc",
                if (values.allocKbPerSecond.isNaN()) "n/a" else "${values.allocKbPerSecond.roundToInt()} kB/s",
                style,
            )
        }
    }
}

@Composable
private fun HudLine(label: String, value: String, style: androidx.compose.ui.text.TextStyle, color: Color? = null) {
    androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth()) {
        Text(label, style = style, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.weight(1f))
        Text(value, style = style, color = color ?: Color.White)
    }
}

/** Total bytes ART has allocated in this process (API 23+), or -1 if unavailable. */
private fun allocatedBytes(): Long = Debug.getRuntimeStat("art.gc.bytes-allocated")?.toLongOrNull() ?: -1L
