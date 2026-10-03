package com.dashwroom.f1telemetry.core.model

/** The flag shown to the player's car (Car Status `m_vehicleFiaFlags`). */
enum class FiaFlag { NONE, GREEN, BLUE, YELLOW }

/** Session `m_safetyCarStatus`. */
enum class SafetyCarMode { NONE, FULL, VIRTUAL, FORMATION_LAP }

/**
 * Everything race control is currently signalling to the player, merged from Car Status (the
 * player's flag), Session (safety car status, marshal zones) and Event packets (SCAR, RDFL, …).
 * Changes rarely; the UI turns transitions into alerts.
 */
data class FlagState(
    val playerFlag: FiaFlag = FiaFlag.NONE,
    val safetyCar: SafetyCarMode = SafetyCarMode.NONE,
    /** The safety car comes in at the end of this lap (SCAR "returning"). */
    val safetyCarEnding: Boolean = false,
    val redFlag: Boolean = false,
    /** Marshal zones currently showing yellow, anywhere on track. */
    val yellowZones: Int = 0,
    /**
     * Increments whenever racing resumes: the player's flag turns green after a yellow, the
     * safety car period ends with "resume race", or the race restarts after a red flag. The UI
     * flashes green once per increment.
     */
    val greenCount: Int = 0,
) {
    val neutralised: Boolean get() = safetyCar == SafetyCarMode.FULL || safetyCar == SafetyCarMode.VIRTUAL
}
