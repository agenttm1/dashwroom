package com.dashwroom.f1telemetry.benchmark

import android.content.Intent
import androidx.benchmark.macro.MacrobenchmarkScope

const val TARGET_PACKAGE = "com.dashwroom.f1telemetry"

/** Launches the app straight into [destination] with the 60 Hz mock ([session] = race or qualifying) as the source. */
fun MacrobenchmarkScope.launchWithMock(destination: String, session: String = "race") {
    startActivityAndWait(
        Intent().apply {
            setPackage(TARGET_PACKAGE)
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            putExtra("source", "mock")
            putExtra("start", destination)
            putExtra("session", session)
        },
    )
}
