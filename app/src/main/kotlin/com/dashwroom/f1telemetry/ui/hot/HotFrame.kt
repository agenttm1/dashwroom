package com.dashwroom.f1telemetry.ui.hot

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.delay
import com.dashwroom.f1telemetry.core.state.HotTelemetry

/**
 * The frame-driven render pattern: once per display frame (at whatever rate the panel runs —
 * 60, 90 or 120 Hz), check whether [HotTelemetry] changed and, if so, bump a single long state.
 * Read the returned state **inside a draw lambda** (Canvas / drawBehind / graphicsLayer) and then
 * read hot values directly: only the draw phase re-runs — no recomposition, no layout — giving
 * one frame of latency with no queue or backlog.
 *
 * The state holds the Choreographer frame time (System.nanoTime base), which interpolating views
 * use together with [HotTelemetry.motionCurrentNanos].
 */
@Composable
fun rememberHotFrame(hot: HotTelemetry, enabled: Boolean = true, everyFrame: Boolean = false): State<Long> {
    val tick: MutableLongState = remember { mutableLongStateOf(0L) }
    LaunchedEffect(hot, enabled, everyFrame) {
        if (!enabled) return@LaunchedEffect
        var lastVersion = -1L
        var lastChangeNanos = 0L
        while (true) {
            // When the data stops changing (paused game, no source) stop asking for frames and
            // poll slowly instead, so an idle dashboard costs no vsync wake-ups.
            if (lastChangeNanos != 0L && System.nanoTime() - lastChangeNanos > IDLE_AFTER_NANOS && hot.version == lastVersion) {
                delay(IDLE_POLL_MS)
                continue
            }
            withFrameNanos { frameNanos ->
                val v = hot.version
                if (v != lastVersion) {
                    lastVersion = v
                    lastChangeNanos = System.nanoTime()
                    tick.longValue = frameNanos
                } else if (everyFrame) {
                    // Interpolating views (track map) keep moving between packets.
                    tick.longValue = frameNanos
                }
            }
        }
    }
    return tick
}

private const val IDLE_AFTER_NANOS = 500_000_000L
private const val IDLE_POLL_MS = 50L
