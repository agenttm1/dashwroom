package com.dashwroom.f1telemetry.ui.format

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import com.dashwroom.f1telemetry.data.settings.DeltaReference
import com.dashwroom.f1telemetry.data.settings.SpeedUnit
import com.dashwroom.f1telemetry.data.settings.TemperatureUnit
import kotlin.math.roundToInt

/** The user's display units and delta reference, provided at the app root. */
@Immutable
data class DisplayPrefs(
    val speed: SpeedUnit = SpeedUnit.KPH,
    val temperature: TemperatureUnit = TemperatureUnit.CELSIUS,
    val deltaReference: DeltaReference = DeltaReference.PERSONAL_BEST,
) {
    fun speed(kph: Int): Int = if (speed == SpeedUnit.MPH) (kph * 0.621371f).roundToInt() else kph

    fun temp(celsius: Int): Int = if (temperature == TemperatureUnit.FAHRENHEIT) (celsius * 9f / 5f + 32f).roundToInt() else celsius

    fun tempText(celsius: Int): String = "${temp(celsius)}${temperature.label}"

    val speedLabel: String get() = speed.label
}

val LocalDisplayPrefs = staticCompositionLocalOf { DisplayPrefs() }
