package com.dashwroom.f1telemetry

import com.dashwroom.f1telemetry.core.model.ConnectionState
import com.dashwroom.f1telemetry.core.model.SourceKind
import com.dashwroom.f1telemetry.core.model.TelemetryStatus
import com.dashwroom.f1telemetry.data.network.NetworkInfo
import com.dashwroom.f1telemetry.data.network.WifiBand
import com.dashwroom.f1telemetry.data.settings.AppSettings
import com.dashwroom.f1telemetry.data.settings.OrientationLock
import com.dashwroom.f1telemetry.data.settings.ScreenKey
import com.dashwroom.f1telemetry.telemetry.TelemetryNotification
import com.dashwroom.f1telemetry.ui.navigation.Destination
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppLogicTest {
    @Test
    fun `wifi band from frequency`() {
        assertThat(NetworkInfo(frequencyMhz = 2437).band).isEqualTo(WifiBand.GHZ_2_4)
        assertThat(NetworkInfo(frequencyMhz = 5180).band).isEqualTo(WifiBand.GHZ_5)
        assertThat(NetworkInfo(frequencyMhz = 5955).band).isEqualTo(WifiBand.GHZ_6)
        assertThat(NetworkInfo(frequencyMhz = null).band).isNull()
    }

    @Test
    fun `notification text reflects connection state`() {
        val searching = TelemetryNotification.content(TelemetryStatus(), "192.168.1.5", 20777)
        assertThat(searching.title).isEqualTo("Searching")
        assertThat(searching.text).contains("192.168.1.5:20777")

        val connected = TelemetryNotification.content(
            TelemetryStatus(connection = ConnectionState.CONNECTED, source = SourceKind.MOCK, telemetryHz = 59.6f, packetsPerSecond = 240f),
            "192.168.1.5", 20777,
        )
        assertThat(connected.title).isEqualTo("Connected · mock data")
        assertThat(connected.text).startsWith("60 Hz · 240 packets/s")

        val error = TelemetryNotification.content(TelemetryStatus(sourceError = "Port 20777 is already in use by another app"), null, 20777)
        assertThat(error.text).contains("already in use")
    }

    @Test
    fun `orientation falls back to per-screen default`() {
        val s = AppSettings(orientationLocks = mapOf(ScreenKey.RACE to OrientationLock.LANDSCAPE))
        assertThat(s.orientationFor(ScreenKey.RACE)).isEqualTo(OrientationLock.LANDSCAPE)
        assertThat(s.orientationFor(ScreenKey.OVERVIEW)).isEqualTo(ScreenKey.OVERVIEW.defaultOrientation)
    }

    @Test
    fun `destinations resolve from launch keys and fill a five-item bottom bar`() {
        assertThat(Destination.fromKey("race")).isEqualTo(Destination.RACE)
        assertThat(Destination.fromKey("nope")).isNull()
        assertThat(Destination.entries.count { it.primary }).isEqualTo(5)
    }
}
