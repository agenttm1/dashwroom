package com.dashwroom.f1telemetry.replay.mock

import com.dashwroom.f1telemetry.core.packet.EventCode
import kotlin.math.min

/**
 * Qualifying behaviour for [MockRace]: every car leaves the garage on a staggered schedule, runs an
 * out lap, one or two flying laps (some invalidated for track limits) and an in lap, then waits
 * in the garage and goes again. The order is by best valid lap. When the clock runs out, cars on
 * a flying lap may finish it; everyone else returns to the garage.
 */
internal class MockQualifyingRules(private val race: MockRace) {
    private val cars get() = race.cars
    private var chequered = false

    fun reset() {
        chequered = false
        race.phase = MockRace.Phase.RACING
        val c = cars
        for (i in 0 until race.carCount) {
            c.driverStatus[i] = MockRace.DRIVER_IN_GARAGE
            c.garageUntil[i] = 4f + i * 7f + race.rng.range(0f, 4f)
            c.lapDistance[i] = 0f
            c.totalDistance[i] = 0f
            c.speed[i] = 0f
            c.lap[i] = 1
            c.flyingLapsLeft[i] = 0
            c.pitStatus[i] = 0
            c.gridPosition[i] = 0
        }
    }

    fun step(dt: Float) {
        val c = cars
        val length = race.track.lengthM
        val now = race.sessionTime
        if (!chequered && now >= race.sessionDurationS) {
            chequered = true
            race.phase = MockRace.Phase.FINISHED
            race.events.raise(EventCode.CHEQUERED_FLAG)
        }
        for (i in 0 until race.carCount) {
            if (c.driverStatus[i] == MockRace.DRIVER_IN_GARAGE) {
                c.speed[i] = 0f
                c.accel[i] = 0f
                val timeLeft = race.sessionDurationS - now
                if (!chequered && now >= c.garageUntil[i] && timeLeft > MIN_TIME_TO_GO_OUT_S) {
                    c.driverStatus[i] = MockRace.DRIVER_OUT_LAP
                    c.lapStartTime[i] = now
                    c.sector[i] = 0
                    c.sector1Ms[i] = 0
                    c.sector2Ms[i] = 0
                    c.lastCheckpoint[i] = -1
                }
                continue
            }
            val s = c.lapDistance[i]
            val factor = when (c.driverStatus[i]) {
                MockRace.DRIVER_FLYING -> 1f
                else -> SLOW_LAP_PACE
            }
            val target = race.track.speedAt(s) * race.paceFactor(i) * factor
            c.accel[i] = (target - c.speed[i]) / dt
            c.speed[i] = target
            val ds = target * dt
            c.lapDistance[i] = s + ds
            c.totalDistance[i] += ds
            race.updateEnergy(i, ds, dt)
            race.updateTiming(i, ds, dt)
            if (c.driverStatus[i] == MockRace.DRIVER_FLYING && !c.lapInvalid[i] &&
                s < length * 0.55f && c.lapDistance[i] >= length * 0.55f && race.rng.nextFloat() < INVALID_LAP_CHANCE
            ) {
                c.lapInvalid[i] = true
                c.warnings[i]++
            }
            if (c.lapDistance[i] >= length) race.completeLap(i, target)
        }
        sortByBestLap()
        if (chequered && race.finishedAt < 0f && (0 until race.carCount).all { c.driverStatus[it] == MockRace.DRIVER_IN_GARAGE }) {
            race.finishedAt = now
            race.events.raise(EventCode.SESSION_ENDED)
        }
    }

    fun onLapCompleted(i: Int) {
        val c = cars
        when (c.driverStatus[i]) {
            MockRace.DRIVER_OUT_LAP -> {
                c.driverStatus[i] = if (chequered) MockRace.DRIVER_IN_LAP else MockRace.DRIVER_FLYING
                c.flyingLapsLeft[i] = 1 + race.rng.nextInt(2)
            }
            MockRace.DRIVER_FLYING -> {
                c.flyingLapsLeft[i]--
                if (c.flyingLapsLeft[i] <= 0 || chequered) c.driverStatus[i] = MockRace.DRIVER_IN_LAP
            }
            MockRace.DRIVER_IN_LAP -> {
                c.driverStatus[i] = MockRace.DRIVER_IN_GARAGE
                c.garageUntil[i] = race.sessionTime + race.rng.range(45f, 110f)
                c.lapDistance[i] = 0f
                // Fresh set of softs for the next run.
                if (c.tyreAge[i] >= 3) race.changeTyres(i, MockRace.VISUAL_SOFT, MockRace.ACTUAL_C3)
            }
        }
    }

    private fun sortByBestLap() {
        val c = cars
        for (a in 1 until race.carCount) {
            val car = c.order[a]
            val key = key(car)
            var b = a - 1
            while (b >= 0 && key(c.order[b]) > key) {
                c.order[b + 1] = c.order[b]
                b--
            }
            c.order[b + 1] = car
        }
        val leaderBest = c.bestLapMs[c.order[0]]
        for (k in 0 until race.carCount) {
            val car = c.order[k]
            c.position[car] = k + 1
            val best = c.bestLapMs[car]
            val ahead = if (k > 0) c.bestLapMs[c.order[k - 1]] else best
            c.gapToFrontMs[car] = if (best > 0 && ahead > 0) min(best - ahead, MAX_GAP).toInt() else 0
            c.gapToLeaderMs[car] = if (best > 0 && leaderBest > 0) min(best - leaderBest, MAX_GAP).toInt() else 0
        }
    }

    private fun key(car: Int): Long = cars.bestLapMs[car].let { if (it > 0) it else Long.MAX_VALUE - 100 + car }

    private companion object {
        const val SLOW_LAP_PACE = 0.8f
        const val INVALID_LAP_CHANCE = 0.12f
        const val MIN_TIME_TO_GO_OUT_S = 150f
        const val MAX_GAP = 3_599_999L
    }
}
