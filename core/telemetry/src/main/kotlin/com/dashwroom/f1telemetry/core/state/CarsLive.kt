package com.dashwroom.f1telemetry.core.state

import com.dashwroom.f1telemetry.core.packet.LapDataPacket
import com.dashwroom.f1telemetry.core.protocol.PacketFormat

/**
 * Plain per-car arrays the ingest thread keeps current from Lap Data, shared by the outline
 * builder, lap timing traces and pit tracking. Ingest thread only.
 */
internal class CarsLive {
    val lapDistance = FloatArray(PacketFormat.MAX_CARS)
    val totalDistance = FloatArray(PacketFormat.MAX_CARS)
    val lapNum = IntArray(PacketFormat.MAX_CARS)
    val currentLapTimeMs = LongArray(PacketFormat.MAX_CARS)
    val lastLapTimeMs = LongArray(PacketFormat.MAX_CARS)
    val sector1Ms = IntArray(PacketFormat.MAX_CARS)
    val sector2Ms = IntArray(PacketFormat.MAX_CARS)
    val pitStatus = IntArray(PacketFormat.MAX_CARS)
    val resultStatus = IntArray(PacketFormat.MAX_CARS)
    val lapInvalid = BooleanArray(PacketFormat.MAX_CARS)
    var numCars = 0
    var trackLengthM = 0f

    // Pit-lane time observation (for the pit-loss estimate).
    private val pitTimerActive = BooleanArray(PacketFormat.MAX_CARS)
    private val pitLaneTimeMs = IntArray(PacketFormat.MAX_CARS)
    private val observedPitTimes = IntArray(PIT_SAMPLES)
    private var observedCount = 0
    private val sortScratch = IntArray(PIT_SAMPLES)

    fun update(p: LapDataPacket) {
        numCars = p.numCars
        for (i in 0 until p.numCars) {
            val c = p.cars[i]
            lapDistance[i] = c.lapDistance
            totalDistance[i] = c.totalDistance
            lapNum[i] = c.currentLapNum
            currentLapTimeMs[i] = c.currentLapTimeMs
            lastLapTimeMs[i] = c.lastLapTimeMs
            sector1Ms[i] = c.sector1TimeMs
            sector2Ms[i] = c.sector2TimeMs
            pitStatus[i] = c.pitStatus
            resultStatus[i] = c.resultStatus
            lapInvalid[i] = c.currentLapInvalid
            if (pitTimerActive[i] && !c.pitLaneTimerActive && pitLaneTimeMs[i] > MIN_PIT_LANE_MS) {
                observedPitTimes[observedCount % PIT_SAMPLES] = pitLaneTimeMs[i]
                observedCount++
            }
            pitTimerActive[i] = c.pitLaneTimerActive
            if (c.pitLaneTimerActive) pitLaneTimeMs[i] = c.pitLaneTimeInLaneMs
        }
    }

    /** Median pit-lane time observed so far, or null. */
    fun medianPitLaneTimeMs(): Int? {
        val n = minOf(observedCount, PIT_SAMPLES)
        if (n == 0) return null
        System.arraycopy(observedPitTimes, 0, sortScratch, 0, n)
        java.util.Arrays.sort(sortScratch, 0, n)
        return sortScratch[n / 2]
    }

    fun reset() {
        lapDistance.fill(0f); totalDistance.fill(0f); lapNum.fill(0); currentLapTimeMs.fill(0)
        lastLapTimeMs.fill(0); sector1Ms.fill(0); sector2Ms.fill(0); pitStatus.fill(0); resultStatus.fill(0)
        lapInvalid.fill(false); pitTimerActive.fill(false); pitLaneTimeMs.fill(0)
        observedCount = 0; numCars = 0; trackLengthM = 0f
    }

    private companion object {
        const val PIT_SAMPLES = 16
        const val MIN_PIT_LANE_MS = 5_000
    }
}
