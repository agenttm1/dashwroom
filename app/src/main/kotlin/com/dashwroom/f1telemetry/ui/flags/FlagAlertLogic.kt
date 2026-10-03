package com.dashwroom.f1telemetry.ui.flags

import androidx.compose.ui.graphics.Color
import com.dashwroom.f1telemetry.core.model.FiaFlag
import com.dashwroom.f1telemetry.core.model.FlagState
import com.dashwroom.f1telemetry.core.model.SafetyCarMode

/** A one-off flash over the whole screen, played when a flag situation starts. */
enum class FlagAlert(val title: String, val subtitle: String, val color: Color, val textColor: Color, val pulses: Int) {
    RED_FLAG("RED FLAG", "Session suspended · return to the pits", FlagColors.Red, Color.White, 3),
    SAFETY_CAR("SAFETY CAR", "Slow down · no overtaking", FlagColors.Yellow, Color.Black, 2),
    VIRTUAL_SAFETY_CAR("VIRTUAL SAFETY CAR", "Keep above the delta · no overtaking", FlagColors.Yellow, Color.Black, 2),
    YELLOW_FLAG("YELLOW FLAG", "Hazard ahead · slow down, no overtaking", FlagColors.Yellow, Color.Black, 3),
    GREEN_FLAG("GREEN FLAG", "Track clear · racing resumes", FlagColors.Green, Color.Black, 2),
}

/** A steady glow around the screen edges while a flag stays out. */
enum class EdgeGlow(val color: Color) { RED(FlagColors.Red), YELLOW(FlagColors.Yellow) }

object FlagColors {
    val Yellow = Color(0xFFFFD000)
    val Red = Color(0xFFE10600)
    val Green = Color(0xFF00C853)
    val Blue = Color(0xFF1E6BFF)
}

/** Pure decision logic, unit-tested: what to flash, what to keep showing. */
object FlagAlertLogic {
    /** The flash to play for the change [prev] → [next], most serious first; null = none. */
    fun alertFor(prev: FlagState, next: FlagState): FlagAlert? = when {
        next.redFlag && !prev.redFlag -> FlagAlert.RED_FLAG
        next.safetyCar == SafetyCarMode.FULL && prev.safetyCar != SafetyCarMode.FULL -> FlagAlert.SAFETY_CAR
        next.safetyCar == SafetyCarMode.VIRTUAL && prev.safetyCar != SafetyCarMode.VIRTUAL -> FlagAlert.VIRTUAL_SAFETY_CAR
        next.greenCount > prev.greenCount -> FlagAlert.GREEN_FLAG
        // Under a safety car every car shows yellow; the banner already says so.
        next.playerFlag == FiaFlag.YELLOW && prev.playerFlag != FiaFlag.YELLOW && !next.neutralised -> FlagAlert.YELLOW_FLAG
        else -> null
    }

    fun edgeGlow(state: FlagState): EdgeGlow? = when {
        state.redFlag -> EdgeGlow.RED
        state.playerFlag == FiaFlag.YELLOW && !state.neutralised -> EdgeGlow.YELLOW
        else -> null
    }

    fun showBlue(state: FlagState): Boolean = state.playerFlag == FiaFlag.BLUE && !state.redFlag
}
