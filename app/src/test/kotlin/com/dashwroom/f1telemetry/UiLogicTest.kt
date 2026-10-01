package com.dashwroom.f1telemetry

import com.dashwroom.f1telemetry.data.settings.SpeedUnit
import com.dashwroom.f1telemetry.data.settings.TemperatureUnit
import com.dashwroom.f1telemetry.ui.format.DisplayPrefs
import com.dashwroom.f1telemetry.ui.format.Fmt
import com.dashwroom.f1telemetry.ui.hot.GlyphBuffer
import com.dashwroom.f1telemetry.data.settings.DrivePreset
import com.dashwroom.f1telemetry.ui.preview.PreviewData
import com.dashwroom.f1telemetry.ui.screens.drive.DriveUiState
import com.dashwroom.f1telemetry.ui.screens.drive.tilesFor
import com.dashwroom.f1telemetry.ui.screens.race.PitAdvice
import com.dashwroom.f1telemetry.ui.screens.race.PitStrategy
import com.google.common.truth.Truth.assertThat
import kotlinx.collections.immutable.toImmutableList
import org.junit.Test

class UiLogicTest {
    @Test
    fun `timing formats`() {
        assertThat(Fmt.lapTime(83_456)).isEqualTo("1:23.456")
        assertThat(Fmt.lapTime(0)).isEqualTo(Fmt.NONE)
        assertThat(Fmt.sector(28_004)).isEqualTo("28.004")
        assertThat(Fmt.gap(1_234)).isEqualTo("+1.234")
        assertThat(Fmt.gap(61_000)).isEqualTo("+1:01.000")
        assertThat(Fmt.delta(-105)).isEqualTo("−0.105")
        assertThat(Fmt.delta(2_284)).isEqualTo("+2.284")
        assertThat(Fmt.clock(3_723)).isEqualTo("1:02:03")
    }

    @Test
    fun `glyph buffer formats without strings`() {
        val b = GlyphBuffer()
        assertThat(String(b.clear().appendLapTime(83_456), 0)).isEqualTo("1:23.456")
        assertThat(String(b.clear().appendDelta(-105), 0)).isEqualTo("−0.105")
        assertThat(String(b.clear().appendInt(7), 0)).isEqualTo("7")
        assertThat(String(b.clear().appendInt(5, 3), 0)).isEqualTo("005")
    }

    private fun String(b: GlyphBuffer, @Suppress("UNUSED_PARAMETER") x: Int) = kotlin.text.String(b.chars, 0, b.length)

    @Test
    fun `unit conversions`() {
        val p = DisplayPrefs(SpeedUnit.MPH, TemperatureUnit.FAHRENHEIT)
        assertThat(p.speed(322)).isEqualTo(200)
        assertThat(p.temp(100)).isEqualTo(212)
        assertThat(p.tempText(20)).isEqualTo("68°F")
        assertThat(DisplayPrefs().speed(322)).isEqualTo(322)
    }

    @Test
    fun `pit strategy projects rejoin position from gaps and pit loss`() {
        val race = PreviewData.race()
        // Player P6, 6.75 s behind the leader; gaps grow by 1.35 s per place; observed loss 21.4 s.
        val advice = PitStrategy.compute(race, PreviewData.info())!!
        assertThat(advice.pitLossMs).isEqualTo(21_400)
        assertThat(advice.pitLossEstimated).isFalse()
        // 6.75 + 21.4 = 28.15 s → behind everyone (the last car is at 25.65 s).
        assertThat(advice.rejoinPosition).isEqualTo(20)
        assertThat(advice.windowState).isEqualTo(PitAdvice.WindowState.OPEN)
        assertThat(advice.carAhead!!.position).isEqualTo(5)
        assertThat(advice.undercutInRange).isTrue()
    }

    @Test
    fun `safety car makes the stop cheaper and moves the rejoin forward`() {
        val race = PreviewData.race().copy(observedPitLaneTimeMs = null)
        val green = PitStrategy.compute(race, PreviewData.info())!!
        val sc = PitStrategy.compute(race, PreviewData.info().copy(safetyCarStatus = 1))!!
        assertThat(green.pitLossEstimated).isTrue()
        assertThat(sc.pitLossMs).isLessThan(green.pitLossMs)
        assertThat(sc.rejoinPosition).isLessThan(green.rejoinPosition)
    }

    @Test
    fun `auto drive preset follows the session type`() {
        val race = PreviewData.info(race = true)
        assertThat(DriveUiState.resolve(DrivePreset.AUTO, race)).isEqualTo(DrivePreset.RACE)
        assertThat(DriveUiState.resolve(DrivePreset.AUTO, race.copy(sessionType = 7))).isEqualTo(DrivePreset.QUALI)
        assertThat(DriveUiState.resolve(DrivePreset.AUTO, race.copy(sessionType = 2))).isEqualTo(DrivePreset.QUALI)
        assertThat(DriveUiState.resolve(DrivePreset.AUTO, race.copy(sessionType = 18))).isEqualTo(DrivePreset.TIME_TRIAL)
        assertThat(DriveUiState.resolve(DrivePreset.AUTO, null)).isEqualTo(DrivePreset.RACE)
        // An explicit choice always wins.
        assertThat(DriveUiState.resolve(DrivePreset.MINIMAL, race)).isEqualTo(DrivePreset.MINIMAL)
    }

    @Test
    fun `race drive tiles show position, gaps and fuel`() {
        val tiles = tilesFor(PreviewData.drive(DrivePreset.RACE), com.dashwroom.f1telemetry.ui.theme.DashColors())
        val byLabel = tiles.associate { it.label to it.value }
        assertThat(byLabel["Position"]).isEqualTo("P6/20")
        assertThat(byLabel["Lap"]).isEqualTo("9/20")
        assertThat(byLabel["Ahead"]).startsWith("HAM +")
        assertThat(byLabel["Fuel"]).isEqualTo("+0.6 L")
        assertThat(tilesFor(PreviewData.drive(DrivePreset.MINIMAL), com.dashwroom.f1telemetry.ui.theme.DashColors())).isEmpty()
    }

    @Test
    fun `no advice without a running player`() {
        val race = PreviewData.race()
        val noPlayer = race.copy(drivers = race.drivers.map { it.copy(isPlayer = false) }.toImmutableList())
        assertThat(PitStrategy.compute(noPlayer, PreviewData.info())).isNull()
    }
}
