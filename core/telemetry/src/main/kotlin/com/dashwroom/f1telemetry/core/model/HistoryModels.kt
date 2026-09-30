package com.dashwroom.f1telemetry.core.model

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf

data class LapRecord(
    val lap: Int,
    val lapTimeMs: Long,
    val sectorsMs: SectorTimes,
    val valid: Boolean,
    val s1Valid: Boolean,
    val s2Valid: Boolean,
    val s3Valid: Boolean,
    /** Visual compound fitted for this lap, from the stint history; 0 if unknown. */
    val tyreVisual: Int,
)

data class TyreStintRecord(
    /** Last lap on this set; null while it's still fitted. */
    val endLap: Int?,
    val actualCompound: Int,
    val visualCompound: Int,
)

/** From Session History: completed laps + stints of one car. */
data class DriverHistory(
    val vehicleIndex: Int,
    val laps: ImmutableList<LapRecord>,
    val stints: ImmutableList<TyreStintRecord>,
    val bestLapNumber: Int,
    val bestSector1LapNumber: Int,
    val bestSector2LapNumber: Int,
    val bestSector3LapNumber: Int,
) {
    val completedLaps: ImmutableList<LapRecord> get() = laps
    val bestLap: LapRecord? get() = laps.firstOrNull { it.lap == bestLapNumber && it.lapTimeMs > 0 }
}

data class FinalResult(
    val vehicleIndex: Int,
    val position: Int,
    val numLaps: Int,
    val gridPosition: Int,
    val points: Int,
    val numPitStops: Int,
    val resultStatus: ResultStatus,
    val bestLapTimeMs: Long,
    val totalRaceTimeSeconds: Double,
    val penaltiesTimeSeconds: Int,
    val stints: ImmutableList<TyreStintRecord>,
)

data class HistoryState(
    val drivers: ImmutableMap<Int, DriverHistory> = persistentMapOf(),
    /** vehicle index → position at the start of each lap (index 0 = lap 1). */
    val lapPositions: ImmutableMap<Int, ImmutableList<Int>> = persistentMapOf(),
    val finalClassification: ImmutableList<FinalResult> = persistentListOf(),
)
