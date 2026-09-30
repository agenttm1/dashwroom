package com.dashwroom.f1telemetry.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Near-black, high-contrast palette readable at a glance from the driving position.
internal val Carbon = Color(0xFF0B0C0F)
internal val CarbonSurface = Color(0xFF121419)
internal val CarbonContainer = Color(0xFF171A20)
internal val CarbonContainerHigh = Color(0xFF1E222A)
internal val CarbonContainerHighest = Color(0xFF262B35)
internal val CarbonOutline = Color(0xFF363C48)
internal val CarbonOutlineVariant = Color(0xFF262B35)
internal val OnCarbon = Color(0xFFEDEFF2)
internal val OnCarbonMuted = Color(0xFF9BA3AF)
internal val RacingRed = Color(0xFFE10600)
internal val RacingRedDark = Color(0xFF8C0400)
internal val Teal = Color(0xFF00C2B2)
internal val Paper = Color(0xFFF6F7F9)

/** Meaning-bearing colours used by timing and status UI, identical in light and dark themes. */
@Immutable
data class DashColors(
    /** Overall session best (sector / lap). */
    val sessionBest: Color = Color(0xFFB14AED),
    /** Personal best. */
    val personalBest: Color = Color(0xFF00D26A),
    /** Slower than personal best. */
    val slower: Color = Color(0xFFFFD500),
    val connected: Color = Color(0xFF00D26A),
    val stale: Color = Color(0xFFFFB300),
    val searching: Color = Color(0xFF8A93A3),
    val warning: Color = Color(0xFFFFB300),
    val danger: Color = Color(0xFFFF3B30),
    val drs: Color = Color(0xFF00D26A),
    val tyreSoft: Color = Color(0xFFFF2D2D),
    val tyreMedium: Color = Color(0xFFFFD12E),
    val tyreHard: Color = Color(0xFFF0F0F0),
    val tyreInter: Color = Color(0xFF43B047),
    val tyreWet: Color = Color(0xFF0067AD),
)

val LocalDashColors = staticCompositionLocalOf { DashColors() }
