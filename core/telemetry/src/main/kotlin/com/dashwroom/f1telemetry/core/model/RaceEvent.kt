package com.dashwroom.f1telemetry.core.model

/** One line in the race-control feed. */
data class RaceEvent(
    val id: Long,
    val sessionTime: Float,
    val lap: Int,
    val type: RaceEventType,
    val text: String,
    val vehicleIndex: Int = -1,
    val otherVehicleIndex: Int = -1,
    /** True when it involves the player (highlight in the feed). */
    val involvesPlayer: Boolean = false,
)

enum class RaceEventType {
    SESSION_STARTED, SESSION_ENDED, FASTEST_LAP, RETIREMENT, DRS_ENABLED, DRS_DISABLED,
    TEAM_MATE_IN_PITS, CHEQUERED_FLAG, RACE_WINNER, PENALTY, SPEED_TRAP, START_LIGHTS,
    LIGHTS_OUT, DRIVE_THROUGH_SERVED, STOP_GO_SERVED, FLASHBACK, RED_FLAG, OVERTAKE,
    SAFETY_CAR, COLLISION, PARTIAL_MODE, OVERTAKE_MODE,
}
