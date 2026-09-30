package com.dashwroom.f1telemetry.ui.debug

import android.util.Log
import android.view.Window
import androidx.metrics.performance.JankStats
import androidx.metrics.performance.PerformanceMetricsState

/**
 * JankStats in debug builds: logs every janky frame (tag "Jank") with the UI state that was on
 * screen, e.g. `screen=Race`. Release builds never create it.
 */
class JankReporter private constructor(window: Window) {
    private val stats = JankStats.createAndTrack(window) { frame ->
        if (frame.isJank) {
            Log.w(TAG, "janky frame ${frame.frameDurationUiNanos / 1_000_000.0} ms ${frame.states.joinToString { "${it.key}=${it.value}" }}")
        }
    }
    private val state = PerformanceMetricsState.getHolderForHierarchy(window.decorView).state

    fun setScreen(name: String) {
        state?.putState("screen", name)
    }

    fun stop() {
        stats.isTrackingEnabled = false
    }

    companion object {
        private const val TAG = "Jank"

        fun createIfDebug(window: Window, debug: Boolean): JankReporter? = if (debug) JankReporter(window) else null
    }
}
