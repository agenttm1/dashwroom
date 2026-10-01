package com.dashwroom.f1telemetry.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.WifiTethering
import androidx.compose.ui.graphics.vector.ImageVector
import com.dashwroom.f1telemetry.data.settings.ScreenKey
import kotlinx.serialization.Serializable

@Serializable data object ConnectRoute
@Serializable data object DriveRoute
@Serializable data object OverviewRoute
@Serializable data object RaceRoute
@Serializable data object QualifyingRoute
@Serializable data object CarRoute
@Serializable data object AnalysisRoute
@Serializable data object SettingsRoute

/**
 * Top-level destinations. [primary] ones fill the phone bottom bar; Connect and
 * Settings join them on the rail / drawer and are otherwise reached from the top bar.
 */
enum class Destination(
    val route: Any,
    val label: String,
    val icon: ImageVector,
    val screen: ScreenKey,
    val primary: Boolean,
) {
    DRIVE(DriveRoute, "Drive", Icons.Outlined.Speed, ScreenKey.DRIVE, true),
    OVERVIEW(OverviewRoute, "Overview", Icons.Outlined.Dashboard, ScreenKey.OVERVIEW, true),
    RACE(RaceRoute, "Race", Icons.Outlined.Flag, ScreenKey.RACE, true),
    QUALIFYING(QualifyingRoute, "Quali", Icons.Outlined.Timer, ScreenKey.QUALIFYING, true),
    CAR(CarRoute, "Car", Icons.Outlined.DirectionsCar, ScreenKey.CAR, true),
    ANALYSIS(AnalysisRoute, "Laps", Icons.Outlined.ShowChart, ScreenKey.ANALYSIS, true),
    CONNECT(ConnectRoute, "Connect", Icons.Outlined.WifiTethering, ScreenKey.CONNECT, false),
    SETTINGS(SettingsRoute, "Settings", Icons.Outlined.Settings, ScreenKey.SETTINGS, false);

    companion object {
        /** Stable name for intents / benchmarks, e.g. "race". */
        fun fromKey(key: String?): Destination? = entries.firstOrNull { it.name.equals(key, ignoreCase = true) }
    }
}
