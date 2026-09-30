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
 * The Race screen is the real target of the criterion (full-rate top strip + 20-car tower with
 * position changes); the others guard against regressions on the busiest remaining screens.
 */
@RunWith(AndroidJUnit4::class)
class FrameTimingBenchmark {
    @get:Rule val rule = MacrobenchmarkRule()

    @Test fun raceScreenUnderMock60Hz() = measure("race")

    @Test fun overviewScreenUnderMock60Hz() = measure("overview")

    @Test fun qualifyingScreenUnderMock60Hz() = measure("qualifying", session = "qualifying")

    @Test fun connectScreenUnderMock60Hz() = measure("connect")

    private fun measure(destination: String, session: String = "race") = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(BaselineProfileMode.UseIfAvailable),
        startupMode = StartupMode.WARM,
        iterations = 3,
        setupBlock = { launchWithMock(destination, session) },
    ) {
        Thread.sleep(RUN_MS)
    }

    private companion object {
        const val RUN_MS = 30_000L
    }
}
