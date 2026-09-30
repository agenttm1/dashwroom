package com.dashwroom.f1telemetry.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Generates the app's Baseline Profile: cold start + visiting every top-level screen with data flowing. */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = TARGET_PACKAGE, includeInStartupProfile = true) {
        pressHome()
        launchWithMock("overview")
        device.wait(Until.hasObject(By.textContains("CONNECTED")), 10_000)
        for (label in listOf("Race", "Quali", "Car", "Laps", "Overview")) {
            device.findObject(By.text(label))?.click()
            device.waitForIdle()
            Thread.sleep(1_500)
        }
        device.findObject(By.descContains("Telemetry"))?.click() // status pill → Connect
        Thread.sleep(3_000)
    }
}
