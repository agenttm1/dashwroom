package com.dashwroom.f1telemetry

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dashwroom.f1telemetry.core.model.ConnectionState
import com.dashwroom.f1telemetry.core.model.GameInfo
import com.dashwroom.f1telemetry.core.model.PacketTypeStats
import com.dashwroom.f1telemetry.core.model.SessionState
import com.dashwroom.f1telemetry.core.model.SourceKind
import com.dashwroom.f1telemetry.core.model.TelemetryStatus
import com.dashwroom.f1telemetry.core.protocol.PacketFormat
import com.dashwroom.f1telemetry.data.network.NetworkInfo
import com.dashwroom.f1telemetry.data.settings.AppSettings
import com.dashwroom.f1telemetry.ui.screens.PlaceholderContent
import com.dashwroom.f1telemetry.ui.screens.connect.ConnectContent
import com.dashwroom.f1telemetry.ui.screens.connect.ConnectUiState
import com.dashwroom.f1telemetry.ui.screens.connect.ConnectWarning
import com.dashwroom.f1telemetry.ui.screens.settings.SettingsContent
import com.dashwroom.f1telemetry.ui.screens.settings.SettingsUiState
import com.dashwroom.f1telemetry.ui.theme.DashwroomTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders real screens on the JVM (Robolectric native graphics) and writes PNGs to
 * `app/screenshots/`. Run `./gradlew :app:recordRoborazziDebug` to refresh them.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class ScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val connected = ConnectUiState(
        status = TelemetryStatus(
            connection = ConnectionState.CONNECTED,
            source = SourceKind.LIVE,
            sourceDescription = "UDP port 20777",
            packetsPerSecond = 243f,
            telemetryHz = 60f,
            bytesPerSecond = 322_000f,
            lossPercent = 0.3f,
            game = GameInfo(2026, PacketFormat.F1_25_SEASON_2026, 26, 1, 22),
            sessionUid = 0x3F2A_1B4C_9D8EL,
            sender = "192.168.1.40",
            totalPackets = 184_210,
            msSinceLastPacket = 3,
            perType = persistentListOf(
                PacketTypeStats(0, "Motion", 45_010, 60f, 0),
                PacketTypeStats(1, "Session", 1_502, 2f, 0),
                PacketTypeStats(2, "Lap Data", 45_010, 60f, 0),
                PacketTypeStats(3, "Event", 212, 0.3f, 0),
                PacketTypeStats(4, "Participants", 150, 0.2f, 0),
                PacketTypeStats(6, "Car Telemetry", 45_010, 60f, 0),
                PacketTypeStats(7, "Car Status", 45_010, 60f, 0),
                PacketTypeStats(16, "Car Telemetry 2", 45_010, 60f, 0),
            ),
            coalescedPackets = 12,
        ),
        network = NetworkInfo(primaryAddress = "192.168.1.23", onWifi = true, frequencyMhz = 5_180),
    )

    private val searching = ConnectUiState(
        network = NetworkInfo(primaryAddress = "192.168.1.23", onWifi = true, frequencyMhz = 2_437),
        warnings = persistentListOf(ConnectWarning.SlowBand),
    )

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
    fun connect_connected_phone() = shoot("connect_connected_phone") { ConnectContent(connected, {}, {}) }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
    fun connect_searching_phone() = shoot("connect_searching_phone") { ConnectContent(searching, {}, {}) }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.MediumTablet)
    fun connect_connected_tablet() = shoot("connect_connected_tablet") { ConnectContent(connected, {}, {}) }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
    fun overview_waiting_phone() = shoot("overview_waiting_phone") {
        PlaceholderContent("Overview", "Phase 2", TelemetryStatus(), SessionState.Empty, onOpenConnect = {})
    }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
    fun settings_phone() = shoot("settings_phone") {
        SettingsContent(SettingsUiState(AppSettings(dataSource = SourceKind.MOCK), loaded = true), vm = null)
    }

    private fun shoot(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent {
            DashwroomTheme {
                androidx.compose.material3.Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.background) { content() }
            }
        }
        compose.onRoot().captureRoboImage("screenshots/$name.png")
    }
}

private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onRoot() =
    onNode(androidx.compose.ui.test.isRoot())
