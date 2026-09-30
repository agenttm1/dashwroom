package com.dashwroom.f1telemetry

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.dashwroom.f1telemetry.core.TelemetryRepository
import com.dashwroom.f1telemetry.core.model.ConnectionState
import com.dashwroom.f1telemetry.core.model.SourceKind
import com.dashwroom.f1telemetry.telemetry.SourceController
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import javax.inject.Inject

/**
 * The Phase 1 deliverable, end to end on the JVM: real Hilt graph, real MainActivity, the mock
 * emitter streaming at 60 Hz through the real parser/pipeline, and the Connect screen showing
 * CONNECTED with live packet counters.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class, sdk = [36], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class AppEndToEndTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var sourceController: SourceController
    @Inject lateinit var repository: TelemetryRepository

    @Before
    fun setUp() {
        hilt.inject()
        // Robolectric doesn't run the foreground service, so drive the controller directly.
        sourceController.overrideSource(SourceKind.MOCK)
        sourceController.start()
    }

    @Test
    fun mockTelemetryReachesTheConnectScreen() {
        compose.waitUntil(15_000) { repository.status.value.connection == ConnectionState.CONNECTED && repository.status.value.telemetryHz > 30f }
        compose.onAllNodesWithText("CONNECTED", substring = true)[0].performClick() // status pill → Connect
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("PACKETS BY TYPE")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Car Telemetry").assertExists()
        compose.onNodeWithText("MOCK").assertExists()

        val status = repository.status.value
        assertThat(status.game!!.packetFormat).isEqualTo(2025)
        assertThat(status.sizeMismatches).isEqualTo(0)
        assertThat(status.perType.map { it.packetId }).containsAtLeast(0, 1, 2, 4, 6, 7)

        compose.waitForIdle()
        compose.onNode(androidx.compose.ui.test.isRoot()).captureRoboImage("screenshots/e2e_connect_mock.png")
        sourceController.stop()
    }
}
