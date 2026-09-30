package com.dashwroom.f1telemetry.core.model

/**
 * The player's telemetry for one completed lap, sampled at [bins] evenly spaced track positions
 * so any two laps overlay directly. Arrays are read-only snapshots.
 */
class LapTrace(
    val sessionUid: Long,
    val lapNumber: Int,
    val lapTimeMs: Long,
    val sectorsMs: SectorTimes,
    val valid: Boolean,
    val trackLengthM: Float,
    val tyreVisual: Int,
    val speedKph: FloatArray,
    val throttle: FloatArray,
    val brake: FloatArray,
    val gear: FloatArray,
    val steer: FloatArray,
    val rpm: FloatArray,
    /** Elapsed lap time (s) at each bin. */
    val time: FloatArray,
) {
    val bins: Int get() = speedKph.size

    /** Metres from the line at [bin]. */
    fun distanceAt(bin: Int): Float = bin * trackLengthM / bins

    override fun equals(other: Any?): Boolean =
        other is LapTrace && other.sessionUid == sessionUid && other.lapNumber == lapNumber

    override fun hashCode(): Int = (sessionUid * 31 + lapNumber).toInt()
}
