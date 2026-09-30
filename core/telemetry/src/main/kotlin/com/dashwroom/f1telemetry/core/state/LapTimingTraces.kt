package com.dashwroom.f1telemetry.core.state

import com.dashwroom.f1telemetry.core.packet.LapDataPacket
import com.dashwroom.f1telemetry.core.protocol.PacketFormat

/**
 * Elapsed-lap-time-versus-distance for every car, so a live delta ("+0.284 to your best") can be
 * computed anywhere on the lap, not just at sector lines. Each car keeps its current lap and its
 * best valid lap; the player also keeps the previous lap and the session-best reference is
 * whichever car holds the fastest valid lap. Pre-allocated; the 60 Hz path never allocates.
 */
internal class LapTimingTraces(private val cars: CarsLive, private val hot: HotTelemetry) {
    private val current = Array(PacketFormat.MAX_CARS) { FloatArray(BINS) { Float.NaN } }
    private val best = Array(PacketFormat.MAX_CARS) { FloatArray(BINS) { Float.NaN } }
    private val bestLapMs = LongArray(PacketFormat.MAX_CARS)
    private val lastBin = IntArray(PacketFormat.MAX_CARS) { -1 }
    private val trackedLap = IntArray(PacketFormat.MAX_CARS)
    private val lapWasInvalid = BooleanArray(PacketFormat.MAX_CARS)
    private val playerPrevious = FloatArray(BINS) { Float.NaN }
    private var playerPreviousValid = false
    private val liveDelta = IntArray(PacketFormat.MAX_CARS) { NO_DELTA }
    private var sessionBestCar = -1

    /** Best valid lap per car as observed here (fallback before Session History arrives). */
    fun bestLapMs(car: Int): Long = bestLapMs[car]

    /** Live delta of [car] to its own best at the same distance, ms, or null. */
    fun liveDeltaToBest(car: Int): Int? = liveDelta[car].takeIf { it != NO_DELTA }

    fun apply(p: LapDataPacket) {
        val length = cars.trackLengthM
        if (length <= 0f) return
        val player = p.header.playerCarIndex
        for (i in 0 until p.numCars) {
            val c = p.cars[i]
            if (c.resultStatus < RESULT_ACTIVE) continue
            if (c.currentLapNum != trackedLap[i]) onNewLap(i, c.currentLapNum, c.lastLapTimeMs, i == player)
            if (c.currentLapInvalid) lapWasInvalid[i] = true
            val d = c.lapDistance
            if (d < 0f || d >= length) continue
            val exact = d / length * BINS
            val bin = exact.toInt().coerceIn(0, BINS - 1)
            val t = c.currentLapTimeMs / 1000f
            val trace = current[i]
            val prev = lastBin[i]
            if (prev in 0 until bin) {
                // Fill skipped bins by linear interpolation so the trace has no holes.
                val t0 = trace[prev]
                val span = bin - prev
                for (k in prev + 1 until bin) trace[k] = if (t0.isNaN()) t else t0 + (t - t0) * (k - prev) / span
            }
            trace[bin] = t
            lastBin[i] = bin
            liveDelta[i] = delta(best[i], exact, t)
            if (i == player) updatePlayerDeltas(exact, t)
        }
    }

    private fun onNewLap(i: Int, newLap: Int, lastLapMs: Long, isPlayer: Boolean) {
        val completed = newLap == trackedLap[i] + 1 && trackedLap[i] > 0
        if (completed && lastLapMs > 0 && coverage(current[i]) >= MIN_COVERAGE) {
            if (isPlayer) {
                System.arraycopy(current[i], 0, playerPrevious, 0, BINS)
                playerPreviousValid = true
            }
            if (!lapWasInvalid[i] && (bestLapMs[i] == 0L || lastLapMs < bestLapMs[i])) {
                bestLapMs[i] = lastLapMs
                System.arraycopy(current[i], 0, best[i], 0, BINS)
                val sb = sessionBestCar
                if (sb < 0 || bestLapMs[sb] == 0L || lastLapMs < bestLapMs[sb]) sessionBestCar = i
            }
        }
        trackedLap[i] = newLap
        lapWasInvalid[i] = false
        lastBin[i] = -1
        current[i].fill(Float.NaN)
    }

    private fun updatePlayerDeltas(exact: Float, t: Float) {
        val player = hot.playerCarIndex
        hot.deltaToPersonalBestMs = delta(best[player], exact, t)
        hot.deltaToSessionBestMs = if (sessionBestCar >= 0) delta(best[sessionBestCar], exact, t) else NO_DELTA
        hot.deltaToLastLapMs = if (playerPreviousValid) delta(playerPrevious, exact, t) else NO_DELTA
    }

    private fun delta(reference: FloatArray, exact: Float, t: Float): Int {
        val b0 = exact.toInt().coerceIn(0, BINS - 1)
        val b1 = (b0 + 1).coerceAtMost(BINS - 1)
        val r0 = reference[b0]
        val r1 = reference[b1]
        if (r0.isNaN()) return NO_DELTA
        val ref = if (r1.isNaN()) r0 else r0 + (r1 - r0) * (exact - b0)
        return ((t - ref) * 1000f).toInt()
    }

    private fun coverage(trace: FloatArray): Float {
        var n = 0
        for (v in trace) if (!v.isNaN()) n++
        return n / BINS.toFloat()
    }

    fun reset() {
        for (i in 0 until PacketFormat.MAX_CARS) {
            current[i].fill(Float.NaN)
            best[i].fill(Float.NaN)
        }
        bestLapMs.fill(0); lastBin.fill(-1); trackedLap.fill(0); lapWasInvalid.fill(false)
        liveDelta.fill(NO_DELTA); playerPrevious.fill(Float.NaN); playerPreviousValid = false; sessionBestCar = -1
    }

    companion object {
        const val BINS = 512
        const val NO_DELTA = Int.MIN_VALUE
        private const val RESULT_ACTIVE = 2
        private const val MIN_COVERAGE = 0.8f
    }
}
