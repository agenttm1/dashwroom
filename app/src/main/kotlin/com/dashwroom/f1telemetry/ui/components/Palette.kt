package com.dashwroom.f1telemetry.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.dashwroom.f1telemetry.ui.theme.DashColors

/** Colour scales shared by the tyre, brake and damage visuals. */
object Palette {
    private val cold = Color(0xFF2F7BFF)
    private val optimal = Color(0xFF00D26A)
    private val warm = Color(0xFFFFD500)
    private val hot = Color(0xFFFF3B30)

    /** Tyre surface/carcass: blue < 80 °C, green 85–105, yellow 110, red ≥ 120. */
    fun tyreTemp(c: Int): Color = scale(c.toFloat(), 70f, 90f, 105f, 120f)

    /** Brakes: blue < 300 °C, green 400–800, yellow 950, red ≥ 1100. */
    fun brakeTemp(c: Int): Color = scale(c.toFloat(), 250f, 450f, 850f, 1100f)

    /** Wear / damage: green 0 → yellow 35 → red ≥ 70 %. */
    fun damage(percent: Float): Color = when {
        percent <= 0f -> optimal
        percent < 35f -> lerp(optimal, warm, percent / 35f)
        percent < 70f -> lerp(warm, hot, (percent - 35f) / 35f)
        else -> hot
    }

    private fun scale(v: Float, coldEdge: Float, lowOk: Float, highOk: Float, hotEdge: Float): Color = when {
        v <= coldEdge -> cold
        v < lowOk -> lerp(cold, optimal, (v - coldEdge) / (lowOk - coldEdge))
        v <= highOk -> optimal
        v < hotEdge -> lerp(warm, hot, (v - highOk) / (hotEdge - highOk))
        else -> hot
    }

    /** Timing colour: purple session best, green personal best, yellow otherwise. */
    fun timing(colors: DashColors, value: Int, personalBest: Int, sessionBest: Int): Color? = when {
        value <= 0 -> null
        sessionBest > 0 && value <= sessionBest -> colors.sessionBest
        personalBest > 0 && value <= personalBest -> colors.personalBest
        else -> colors.slower
    }

    /** Delta: negative (faster) green, positive red. */
    fun delta(colors: DashColors, ms: Int): Color = if (ms <= 0) colors.personalBest else colors.danger
}
