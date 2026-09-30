package com.dashwroom.f1telemetry.replay.mock

/** Struct-of-arrays state for every simulated car. Pre-allocated; the step loop never allocates. */
class MockCars(val count: Int) {
    val lapDistance = FloatArray(count)
    val totalDistance = FloatArray(count)
    /** Sort key: total distance while racing, frozen finishing rank afterwards. */
    val rankKey = FloatArray(count)
    val lap = IntArray(count)
    val speed = FloatArray(count)
    val accel = FloatArray(count)
    val lapStartTime = FloatArray(count)
    val lastLapMs = LongArray(count)
    val bestLapMs = LongArray(count)
    val sector = IntArray(count)
    val sector1Ms = IntArray(count)
    val sector2Ms = IntArray(count)
    val position = IntArray(count)
    val previousPosition = IntArray(count)
    val gridPosition = IntArray(count)
    val order = IntArray(count)
    val gapToFrontMs = IntArray(count)
    val gapToLeaderMs = IntArray(count)

    /** 0 none, 1 pitting (in lane), 2 in pit area (stationary). */
    val pitStatus = IntArray(count)
    val pitStops = IntArray(count)
    val plannedPitLap = IntArray(count)
    val pitStopRemaining = FloatArray(count)
    val pitStopTimerMs = IntArray(count)
    val pitLaneTimeMs = IntArray(count)
    val visualCompound = IntArray(count)
    val actualCompound = IntArray(count)
    val tyreAge = IntArray(count)
    val tyreWear = FloatArray(count)
    val fuel = FloatArray(count)
    val ersStore = FloatArray(count)
    val ersHarvestedLap = FloatArray(count)
    val ersDeployedLap = FloatArray(count)
    val brakeTemp = FloatArray(count)

    /** 2 active, 3 finished, 7 retired (LapData result status values). */
    val resultStatus = IntArray(count)
    val penaltiesSeconds = IntArray(count)
    val warnings = IntArray(count)
    val drsOpen = BooleanArray(count)
    val drsAllowed = BooleanArray(count)
    val lapNoise = FloatArray(count)
    val laneOffset = FloatArray(count)

    // Timing checkpoints, two laps deep (indexed by lap parity), for realistic interval timing.
    val lastCheckpoint = IntArray(count)
    val checkpointTime = FloatArray(count * 2 * CHECKPOINTS)
    val checkpointLap = IntArray(count * 2 * CHECKPOINTS)

    fun checkpointSlot(car: Int, lap: Int, cp: Int): Int = (car * 2 + (lap and 1)) * CHECKPOINTS + cp

    companion object {
        const val CHECKPOINTS = 64
    }
}
