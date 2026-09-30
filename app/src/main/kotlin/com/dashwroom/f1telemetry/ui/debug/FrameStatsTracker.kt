package com.dashwroom.f1telemetry.ui.debug

import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.view.FrameMetrics
import android.view.Window
import java.util.concurrent.atomic.AtomicLong

/**
 * Counts frames actually rendered by the window (via FrameMetrics) and how many missed their
 * deadline. On API 31+ the per-frame deadline comes from the system (so 90/120 Hz panels are
 * judged correctly); earlier, the display's refresh interval is used.
 */
class FrameStatsTracker(private val window: Window) {
    val frames = AtomicLong()
    val jankyFrames = AtomicLong()
    private val thread = HandlerThread("frame-metrics").apply { start() }
    private val refreshIntervalNanos: Long =
        (1_000_000_000.0 / (window.decorView.display?.refreshRate?.takeIf { it > 0f } ?: 60f)).toLong()

    private val listener = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
        frames.incrementAndGet()
        val total = metrics.getMetric(FrameMetrics.TOTAL_DURATION)
        val deadline = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) metrics.getMetric(FrameMetrics.DEADLINE) else refreshIntervalNanos
        if (total > deadline) jankyFrames.incrementAndGet()
    }

    fun start() {
        window.addOnFrameMetricsAvailableListener(listener, Handler(thread.looper))
    }

    fun stop() {
        runCatching { window.removeOnFrameMetricsAvailableListener(listener) }
        thread.quitSafely()
    }
}
