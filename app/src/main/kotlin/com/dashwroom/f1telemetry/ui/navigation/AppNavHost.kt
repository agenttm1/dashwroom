package com.dashwroom.f1telemetry.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.dashwroom.f1telemetry.ui.screens.analysis.AnalysisScreen
import com.dashwroom.f1telemetry.ui.screens.car.CarScreen
import com.dashwroom.f1telemetry.ui.screens.connect.ConnectScreen
import com.dashwroom.f1telemetry.ui.screens.overview.OverviewScreen
import com.dashwroom.f1telemetry.ui.screens.qualifying.QualifyingScreen
import com.dashwroom.f1telemetry.ui.screens.race.RaceScreen
import com.dashwroom.f1telemetry.ui.screens.settings.SettingsScreen

@Composable
fun AppNavHost(
    navController: NavHostController,
    start: Destination,
    onNavigate: (Destination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val openConnect = { onNavigate(Destination.CONNECT) }
    NavHost(
        navController = navController,
        startDestination = start.route,
        modifier = modifier,
        enterTransition = { fadeIn() },
        exitTransition = { fadeOut() },
    ) {
        composable<OverviewRoute> { OverviewScreen(onOpenConnect = openConnect) }
        composable<RaceRoute> { RaceScreen(onOpenConnect = openConnect) }
        composable<QualifyingRoute> { QualifyingScreen(onOpenConnect = openConnect) }
        composable<CarRoute> { CarScreen(onOpenConnect = openConnect) }
        composable<AnalysisRoute> { AnalysisScreen(onOpenConnect = openConnect) }
        composable<ConnectRoute> { ConnectScreen() }
        composable<SettingsRoute> { SettingsScreen() }
    }
}
