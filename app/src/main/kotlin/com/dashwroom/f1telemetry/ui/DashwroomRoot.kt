package com.dashwroom.f1telemetry.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.dashwroom.f1telemetry.BuildConfig
import com.dashwroom.f1telemetry.core.TelemetryRepository
import com.dashwroom.f1telemetry.data.settings.OrientationLock
import com.dashwroom.f1telemetry.data.settings.SettingsRepository
import com.dashwroom.f1telemetry.ui.components.KeepScreenOn
import com.dashwroom.f1telemetry.ui.debug.DebugHud
import com.dashwroom.f1telemetry.ui.debug.JankReporter
import com.dashwroom.f1telemetry.ui.navigation.AppScaffold
import com.dashwroom.f1telemetry.ui.navigation.Destination
import com.dashwroom.f1telemetry.ui.theme.DashwroomTheme

/** App root: theme + density, keep-screen-on, per-screen orientation, debug HUD, JankStats. */
@Composable
fun DashwroomRoot(
    activity: Activity,
    repository: TelemetryRepository,
    settingsRepository: SettingsRepository,
    startDestination: Destination,
) {
    val settings by settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null)
    val status by repository.status.collectAsStateWithLifecycle()
    val s = settings ?: return // DataStore loads in a few ms; the window background covers it.

    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val current = Destination.entries.firstOrNull { d ->
        backStack?.destination?.hierarchy?.any { it.hasRoute(d.route::class) } == true
    } ?: startDestination

    val lock = s.orientationFor(current.screen)
    LaunchedEffect(lock) {
        activity.requestedOrientation = when (lock) {
            OrientationLock.UNLOCKED -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            OrientationLock.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            OrientationLock.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    val jank = remember(activity) { JankReporter.createIfDebug(activity.window, BuildConfig.DEBUG) }
    DisposableEffect(jank) { onDispose { jank?.stop() } }
    LaunchedEffect(current) { jank?.setScreen(current.label) }

    KeepScreenOn(enabled = s.keepScreenOn && status.isReceiving)

    DashwroomTheme(themeMode = s.themeMode, density = s.uiDensity) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            AppScaffold(navController = navController, start = startDestination, status = status)
            if (s.debugHud) {
                DebugHud(
                    status = status,
                    hot = repository.hot,
                    modifier = Modifier.align(Alignment.BottomEnd).windowInsetsPadding(WindowInsets.safeDrawing),
                )
            }
        }
    }
}
