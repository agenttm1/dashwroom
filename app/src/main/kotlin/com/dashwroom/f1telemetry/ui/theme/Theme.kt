package com.dashwroom.f1telemetry.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.dashwroom.f1telemetry.data.settings.ThemeMode
import com.dashwroom.f1telemetry.data.settings.UiDensity

private val DarkScheme = darkColorScheme(
    primary = RacingRed,
    onPrimary = OnCarbon,
    primaryContainer = RacingRedDark,
    onPrimaryContainer = OnCarbon,
    secondary = Teal,
    onSecondary = Carbon,
    tertiary = LocalDashColorsDefaults.sessionBest,
    background = Carbon,
    onBackground = OnCarbon,
    surface = CarbonSurface,
    onSurface = OnCarbon,
    surfaceVariant = CarbonContainerHigh,
    onSurfaceVariant = OnCarbonMuted,
    surfaceContainerLowest = Carbon,
    surfaceContainerLow = CarbonSurface,
    surfaceContainer = CarbonContainer,
    surfaceContainerHigh = CarbonContainerHigh,
    surfaceContainerHighest = CarbonContainerHighest,
    outline = CarbonOutline,
    outlineVariant = CarbonOutlineVariant,
    error = LocalDashColorsDefaults.danger,
)

private val LightScheme = lightColorScheme(
    primary = RacingRed,
    onPrimary = Paper,
    secondary = Teal,
    background = Paper,
    surface = Paper,
    error = LocalDashColorsDefaults.danger,
)

private object LocalDashColorsDefaults {
    val sessionBest = DashColors().sessionBest
    val danger = DashColors().danger
}

/**
 * App theme. Dark by default. [density] scales the whole UI (dp and sp alike) on top of the user's
 * own font-scale setting, for tablets mounted far from the driver.
 */
@Composable
fun DashwroomTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    density: UiDensity = UiDensity.NORMAL,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val base = LocalDensity.current
    val scaled = if (density == UiDensity.NORMAL) base else Density(base.density * density.scale, base.fontScale)
    CompositionLocalProvider(LocalDensity provides scaled, LocalDashColors provides DashColors()) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            typography = DashTypography,
            content = content,
        )
    }
}

object DashTheme {
    val colors: DashColors
        @Composable get() = LocalDashColors.current
}
