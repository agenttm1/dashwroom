package com.dashwroom.f1telemetry.core.model

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** Everything the timing tower knows about one car. Immutable; rebuilt at ≤10 Hz when it changes. */
data class DriverState(
    val vehicleIndex: Int,
    val name: String,
    /** Three-letter timing code, e.g. "NOR". */
    val code: String,
    val teamId: Int,
    val teamName: String,
    /** 0xRRGGBB from the car's livery, when the game sends one. */
    val liveryColour: Int?,
    val raceNumber: Int,
    val isPlayer: Boolean,
    val aiControlled: Boolean,
    val telemetryPublic: Boolean,
    val position: Int,
    val gridPosition: Int,
    val currentLap: Int,
    val lapDistance: Float,
    val totalDistance: Float,
    val currentLapTimeMs: Long,
    val lastLapTimeMs: Long,
    /** 0 = no timed lap yet. */
    val bestLapTimeMs: Long,
    /** 0, 1, 2 = sector the car is currently in. */
    val sector: Int,
    val currentSector1Ms: Int,
    val currentSector2Ms: Int,
    val lastSectorsMs: SectorTimes?,
    val bestSectorsMs: SectorTimes?,
    /** Live delta to this driver's own best lap at the same track position, ms; null if unknown. */
    val liveDeltaToBestMs: Int?,
    val intervalMs: Int,
    val gapToLeaderMs: Int,
    val lapsBehindLeader: Int,
    val pitStatus: PitStatus,
    val numPitStops: Int,
    val penaltiesSeconds: Int,
    val totalWarnings: Int,
    val unservedDriveThroughs: Int,
    val unservedStopGoes: Int,
    val driverStatus: DriverStatus,
    val resultStatus: ResultStatus,
    val currentLapInvalid: Boolean,
    val tyreVisual: Int,
    val tyreActual: Int,
    val tyreAgeLaps: Int,
    val drsOpen: Boolean,
    val drsAllowed: Boolean,
    /** -1 unknown, 0 none, 1 green, 2 blue, 3 yellow. */
    val fiaFlag: Int,
    /** 2026: Overtake Mode active. */
    val overtakeActive: Boolean,
) {
    val isRunning: Boolean get() = resultStatus == ResultStatus.ACTIVE
    val isOnFlyingLap: Boolean get() = driverStatus == DriverStatus.FLYING_LAP
}

data class SectorTimes(val s1Ms: Int, val s2Ms: Int, val s3Ms: Int) {
    fun get(sector: Int): Int = when (sector) {
        0 -> s1Ms
        1 -> s2Ms
        else -> s3Ms
    }
}

enum class PitStatus { NONE, PITTING, IN_PIT_AREA }

enum class DriverStatus { IN_GARAGE, FLYING_LAP, IN_LAP, OUT_LAP, ON_TRACK }

enum class ResultStatus { INVALID, INACTIVE, ACTIVE, FINISHED, DID_NOT_FINISH, DISQUALIFIED, NOT_CLASSIFIED, RETIRED }

/** Best lap and best individual sectors of the whole session, with who set them. */
data class SessionBests(
    val lapMs: Long = 0,
    val lapVehicle: Int = -1,
    val s1Ms: Int = 0,
    val s1Vehicle: Int = -1,
    val s2Ms: Int = 0,
    val s2Vehicle: Int = -1,
    val s3Ms: Int = 0,
    val s3Vehicle: Int = -1,
) {
    fun sectorMs(sector: Int): Int = when (sector) {
        0 -> s1Ms
        1 -> s2Ms
        else -> s3Ms
    }
}

data class RaceState(
    /** Running cars sorted by position, then retired/inactive ones. */
    val drivers: ImmutableList<DriverState> = persistentListOf(),
    val bests: SessionBests = SessionBests(),
    val leaderLap: Int = 0,
    /** Median observed pit-lane time this session (entry to exit), ms; null until someone has pitted. */
    val observedPitLaneTimeMs: Int? = null,
) {
    val player: DriverState? get() = drivers.firstOrNull { it.isPlayer }

    fun driver(vehicleIndex: Int): DriverState? = drivers.firstOrNull { it.vehicleIndex == vehicleIndex }
}
