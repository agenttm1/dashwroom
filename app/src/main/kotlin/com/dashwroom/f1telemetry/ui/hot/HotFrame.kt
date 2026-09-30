package com.dashwroom.f1telemetry.ui.hot

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import com.dashwroom.f1telemetry.core.state.HotTelemetry

/**
 * The frame-driven render pattern: once per display frame (at whatever rate the panel runs —
 * 60, 90 or 120 Hz), check whether [HotTelemetry] changed and, if so, bump a single long state.
 * Read the returned state **inside a draw lambda** (Canvas / drawBehind / graphicsLayer) and then
 * read hot values directly: only the draw phase re-runs — no recomposition, no layout — giving
 * one frame of latency with no queue or backlog.
 */
@Composable
fun rememberHotFrame(hot: HotTelemetry, enabled: Boolean = true): State<Long> {
    val tick: MutableLongState = remember { mutableLongStateOf(0L) }
    LaunchedEffect(hot, enabled) {
        if (!enabled) return@LaunchedEffect
        var lastVersion = -1L
        while (true) {
            withFrameNanos { frameNanos ->
                val v = hot.version
                if (v != lastVersion) {
                    lastVersion = v
                    tick.longValue = frameNanos
                }
            }
        }
    }
    return tick
}
