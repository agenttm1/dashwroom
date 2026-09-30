package com.dashwroom.f1telemetry.core.model

import com.dashwroom.f1telemetry.core.protocol.PacketFormat
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * The coherent, slow-changing picture of the session, rebuilt only when something relevant
 * changes. High-rate values (speed, RPM, positions) live in
 * [com.dashwroom.f1telemetry.core.state.HotTelemetry] instead. Phase 1 carries session basics and
 * participants; Phase 2 grows it into the full race model.
 */
data class SessionState(
    val sessionUid: Long = 0L,
    val format: PacketFormat? = null,
    val playerCarIndex: Int = 0,
    val info: SessionInfo? = null,
    val numActiveCars: Int = 0,
    val participants: ImmutableList<ParticipantInfo> = persistentListOf(),
) {
    val hasData: Boolean get() = info != null || participants.isNotEmpty()

    companion object {
        val Empty = SessionState()
    }
}

data class SessionInfo(
    val trackId: Int,
    val trackName: String,
    val trackLengthM: Int,
    val sessionType: Int,
    val sessionTypeName: String,
    val weather: Int,
    val weatherName: String,
    val trackTemperatureC: Int,
    val airTemperatureC: Int,
    val totalLaps: Int,
    val sessionTimeLeftS: Int,
    val sessionDurationS: Int,
    val safetyCarStatus: Int,
    val formula: Int,
    val pitSpeedLimitKph: Int,
    val gamePaused: Boolean,
    val networkGame: Boolean,
)

data class ParticipantInfo(
    val vehicleIndex: Int,
    val name: String,
    val driverId: Int,
    val teamId: Int,
    val teamName: String,
    val raceNumber: Int,
    val nationality: Int,
    val aiControlled: Boolean,
    val telemetryPublic: Boolean,
    /** 0xRRGGBB of the first livery colour, or null if the game sent none. */
    val liveryColour: Int?,
)
