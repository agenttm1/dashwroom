package com.dashwroom.f1telemetry.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.window.core.layout.WindowSizeClass
import com.dashwroom.f1telemetry.core.model.TelemetryStatus
import com.dashwroom.f1telemetry.ui.components.StatusPill

/**
 * Window-size-class driven shell: bottom bar on compact width (<600dp), navigation rail on
 * medium (600–840dp), permanent drawer on expanded (>840dp).
 */
@Composable
fun AppScaffold(
    navController: NavHostController,
    start: Destination,
    status: TelemetryStatus,
) {
    val backStack by navController.currentBackStackEntryAsState()
    val current = Destination.entries.firstOrNull { d ->
        backStack?.destination?.hierarchy?.any { it.hasRoute(d.route::class) } == true
    } ?: start

    val sizeClass = currentWindowAdaptiveInfo().windowSizeClass
    val layoutType = when {
        sizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND) -> NavigationSuiteType.NavigationDrawer
        else -> NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfo())
    }
    val compactBar = layoutType == NavigationSuiteType.NavigationBar
    val items = if (compactBar) Destination.entries.filter { it.primary } else Destination.entries

    val navigate: (Destination) -> Unit = { destination ->
        navController.navigate(destination.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    NavigationSuiteScaffold(
        layoutType = layoutType,
        navigationSuiteItems = {
            items.forEach { d ->
                item(
                    selected = d == current,
                    onClick = { navigate(d) },
                    icon = { Icon(d.icon, contentDescription = null) },
                    label = { Text(d.label) },
                )
            }
        },
    ) {
        Column(Modifier.fillMaxSize()) {
            TopBar(
                title = current.label,
                status = status,
                showSettings = compactBar && current != Destination.SETTINGS,
                onStatusClick = { navigate(Destination.CONNECT) },
                onSettingsClick = { navigate(Destination.SETTINGS) },
            )
            AppNavHost(navController, start, navigate, Modifier.weight(1f))
        }
    }
}

@Composable
private fun TopBar(
    title: String,
    status: TelemetryStatus,
    showSettings: Boolean,
    onStatusClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.End))
            .heightIn(min = 52.dp)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        StatusPill(status = status, onClick = onStatusClick)
        if (showSettings) {
            IconButton(onClick = onSettingsClick) { Icon(Icons.Outlined.Settings, contentDescription = "Settings") }
        } else {
            Spacer(Modifier.width(12.dp))
        }
    }
}
