package com.dashwroom.f1telemetry.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Frame timing while the mock emitter streams at 60 Hz, 30 s per iteration.
 * Acceptance (addendum B): zero janky frames, P99 frame duration < 16 ms.
 *
 * Phase 1 measures the Connect screen (live diagnostics); Phase 2 adds the Race screen, the
 * real target of the criterion, by passing "race".
 */
@RunWith(AndroidJUnit4::class)
class FrameTimingBenchmark {
    @get:Rule val rule = MacrobenchmarkRule()

    @Test fun connectScreenUnderMock60Hz() = measure("connect")

    private fun measure(destination: String) = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(BaselineProfileMode.UseIfAvailable),
        startupMode = StartupMode.WARM,
        iterations = 3,
        setupBlock = { launchWithMock(destination) },
    ) {
        Thread.sleep(RUN_MS)
    }

    private companion object {
        const val RUN_MS = 30_000L
    }
}
