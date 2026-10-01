package com.dashwroom.f1telemetry.ui.navigation

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Full-screen driving mode: hides the navigation chrome and the system bars so the Drive screen
 * gets every pixel. Only honoured while the Drive screen is showing.
 */
@Stable
class ImmersiveController {
    var enabled by mutableStateOf(false)
}

val LocalImmersive = staticCompositionLocalOf { ImmersiveController() }
