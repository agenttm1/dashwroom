package com.dashwroom.f1telemetry.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Tabular (fixed-width) digits, so timing values never jitter sideways as they change. */
private const val TABULAR = "tnum"

private val base = Typography()

private fun TextStyle.tabular() = copy(fontFeatureSettings = TABULAR)

val DashTypography = Typography(
    displayLarge = base.displayLarge.tabular().copy(fontWeight = FontWeight.SemiBold),
    displayMedium = base.displayMedium.tabular().copy(fontWeight = FontWeight.SemiBold),
    displaySmall = base.displaySmall.tabular().copy(fontWeight = FontWeight.SemiBold),
    headlineLarge = base.headlineLarge.tabular().copy(fontWeight = FontWeight.SemiBold),
    headlineMedium = base.headlineMedium.tabular().copy(fontWeight = FontWeight.SemiBold),
    headlineSmall = base.headlineSmall.tabular().copy(fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.tabular().copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.tabular().copy(fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.tabular(),
    bodyLarge = base.bodyLarge.tabular(),
    bodyMedium = base.bodyMedium.tabular(),
    bodySmall = base.bodySmall.tabular(),
    labelLarge = base.labelLarge.tabular().copy(fontWeight = FontWeight.SemiBold),
    labelMedium = base.labelMedium.tabular(),
    labelSmall = base.labelSmall.tabular().copy(letterSpacing = 0.8.sp),
)

/** Monospaced tabular style for dense diagnostic tables. */
val MonoNumbers = TextStyle(fontFamily = FontFamily.Monospace, fontFeatureSettings = TABULAR)
