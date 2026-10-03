package com.dashwroom.f1telemetry

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dashwroom.f1telemetry.core.model.FiaFlag
import com.dashwroom.f1telemetry.core.model.FlagState
import com.dashwroom.f1telemetry.core.model.SafetyCarMode
import com.dashwroom.f1telemetry.data.settings.DrivePreset
import com.dashwroom.f1telemetry.ui.flags.BannerKind
import com.dashwroom.f1telemetry.ui.flags.EdgeGlow
import com.dashwroom.f1telemetry.ui.flags.FlagAlert
import com.dashwroom.f1telemetry.ui.flags.FlagAlertLogic
import com.dashwroom.f1telemetry.ui.flags.FlagOverlay
import com.dashwroom.f1telemetry.ui.flags.RaceControlBanner
import com.dashwroom.f1telemetry.ui.preview.PreviewData
import com.dashwroom.f1telemetry.ui.screens.drive.DriveContent
import com.dashwroom.f1telemetry.ui.screens.race.PitStrategy
import com.dashwroom.f1telemetry.ui.screens.race.RaceContent
import com.dashwroom.f1telemetry.ui.screens.race.RaceUiState
import com.dashwroom.f1telemetry.ui.theme.DashwroomTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class FlagAlertsTest {
    @get:Rule val compose = createComposeRule()

    private val none = FlagState()
    private val yellow = FlagState(playerFlag = FiaFlag.YELLOW, yellowZones = 2)
    private val sc = FlagState(playerFlag = FiaFlag.YELLOW, safetyCar = SafetyCarMode.FULL, yellowZones = 6)

    // ---- logic ----

    @Test
    fun `alerts fire on transitions only, most serious first`() {
        assertThat(FlagAlertLogic.alertFor(none, yellow)).isEqualTo(FlagAlert.YELLOW_FLAG)
        assertThat(FlagAlertLogic.alertFor(yellow, yellow)).isNull()
        assertThat(FlagAlertLogic.alertFor(yellow, sc)).isEqualTo(FlagAlert.SAFETY_CAR)
        assertThat(FlagAlertLogic.alertFor(none, none.copy(safetyCar = SafetyCarMode.VIRTUAL))).isEqualTo(FlagAlert.VIRTUAL_SAFETY_CAR)
        assertThat(FlagAlertLogic.alertFor(sc, sc.copy(redFlag = true))).isEqualTo(FlagAlert.RED_FLAG)
        assertThat(FlagAlertLogic.alertFor(sc, none.copy(greenCount = 1))).isEqualTo(FlagAlert.GREEN_FLAG)
        // Every car is yellow under the safety car: no extra yellow flash.
        assertThat(FlagAlertLogic.alertFor(none.copy(safetyCar = SafetyCarMode.FULL), sc)).isNull()
        assertThat(FlagAlertLogic.alertFor(none, none.copy(playerFlag = FiaFlag.BLUE))).isNull()
    }

    @Test
    fun `edge glow, blue badge and banner follow the current state`() {
        assertThat(FlagAlertLogic.edgeGlow(yellow)).isEqualTo(EdgeGlow.YELLOW)
        assertThat(FlagAlertLogic.edgeGlow(sc)).isNull()
        assertThat(FlagAlertLogic.edgeGlow(sc.copy(redFlag = true))).isEqualTo(EdgeGlow.RED)
        assertThat(FlagAlertLogic.showBlue(none.copy(playerFlag = FiaFlag.BLUE))).isTrue()
        assertThat(BannerKind.of(none)).isNull()
        assertThat(BannerKind.of(sc)).isEqualTo(BannerKind.SAFETY_CAR)
        assertThat(BannerKind.of(sc.copy(redFlag = true))).isEqualTo(BannerKind.RED_FLAG)
        assertThat(BannerKind.subtitle(BannerKind.SAFETY_CAR, sc.copy(safetyCarEnding = true))).contains("in this lap")
    }

    // ---- rendering ----

    private fun race(): RaceUiState {
        val r = PreviewData.race()
        return RaceUiState(true, PreviewData.info(), r, PreviewData.history(), PreviewData.events(), pitAdvice = PitStrategy.compute(r, PreviewData.info()))
    }

    @Composable
    private fun RaceWithFlags(flags: FlagState) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                RaceControlBanner(flags)
                RaceContent(race(), PreviewData.hot(), PreviewData.frame(), {}, {}, {}, Modifier.weight(1f))
            }
            FlagOverlay(flags, flashes = true)
        }
    }

    private fun shootTransition(name: String, to: FlagState, atMs: Long, content: @Composable (FlagState) -> Unit) {
        var flags by mutableStateOf(none)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            DashwroomTheme { Surface(color = MaterialTheme.colorScheme.background) { content(flags) } }
        }
        compose.mainClock.advanceTimeBy(100)
        compose.runOnUiThread {
            flags = to
            Snapshot.sendApplyNotifications()
        }
        compose.mainClock.advanceTimeBy(atMs)
        compose.onRoot().captureRoboImage("screenshots/$name.png")
    }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.MediumTablet)
    fun yellow_flag_flash_tablet() {
        shootTransition("flag_yellow_flash_tablet", yellow, 200) { RaceWithFlags(it) }
        compose.onNodeWithContentDescription("YELLOW FLAG", substring = true).assertExists()
    }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.MediumTablet)
    fun safety_car_banner_tablet() =
        shootTransition("flag_safety_car_tablet", sc.copy(safetyCarEnding = true), 5_000) { RaceWithFlags(it) }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.MediumTablet)
    fun red_flag_tablet() = shootTransition("flag_red_tablet", FlagState(redFlag = true), 220) { RaceWithFlags(it) }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
    fun blue_flag_drive_phone() = shootTransition("flag_blue_drive_phone", FlagState(playerFlag = FiaFlag.BLUE), 1_000) { f ->
        Box(Modifier.fillMaxSize()) {
            DriveContent(PreviewData.drive(DrivePreset.RACE), PreviewData.hot(), PreviewData.frame(), {}, false, {}, {})
            FlagOverlay(f, flashes = true)
        }
    }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
    fun vsc_drive_phone() = shootTransition("flag_vsc_drive_phone", FlagState(safetyCar = SafetyCarMode.VIRTUAL), 5_000) { f ->
        Column(Modifier.fillMaxSize()) {
            RaceControlBanner(f)
            DriveContent(PreviewData.drive(DrivePreset.RACE), PreviewData.hot(), PreviewData.frame(), {}, false, {}, {}, Modifier.weight(1f))
        }
    }
}
