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
    val pitStopWindowIdealLap: Int = 0,
    val pitStopWindowLatestLap: Int = 0,
    val pitStopRejoinPosition: Int = 0,
    val sector2StartM: Float = 0f,
    val sector3StartM: Float = 0f,
    val numSafetyCarPeriods: Int = 0,
    val numVirtualSafetyCarPeriods: Int = 0,
    val numRedFlagPeriods: Int = 0,
    /** Yellow/blue/green flags per marshal zone (−1 unknown, 0 none, 1 green, 2 blue, 3 yellow). */
    val marshalZoneFlags: ImmutableList<Int> = persistentListOf(),
    val forecast: ImmutableList<WeatherSample> = persistentListOf(),
    val ruleSet: Int = 0,
    val gameMode: Int = 0,
) {
    val isRace: Boolean get() = sessionType in 15..17
    val isQualifying: Boolean get() = sessionType in 5..14
    val isPractice: Boolean get() = sessionType in 1..4
    val isTimeTrial: Boolean get() = sessionType == 18
}

data class WeatherSample(
    val timeOffsetMinutes: Int,
    val weather: Int,
    val trackTemperatureC: Int,
    val airTemperatureC: Int,
    val rainPercentage: Int,
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
