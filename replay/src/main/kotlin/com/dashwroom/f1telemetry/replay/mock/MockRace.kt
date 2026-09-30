package com.dashwroom.f1telemetry.replay.mock

import com.dashwroom.f1telemetry.core.packet.EventCode
import com.dashwroom.f1telemetry.core.protocol.PacketFormat
import kotlin.math.max
import kotlin.math.min

/**
 * A small, deterministic race simulation: start lights, racing with dirty air and DRS, pit stops,
 * tyre wear, fuel and ERS, a safety car period, a penalty, a retirement and the chequered flag.
 * It exists purely to feed realistic-looking packets through the real parser; nothing in the app
 * special-cases mock data.
 *
 * [step] advances time and never allocates.
 */
class MockRace(
    val format: PacketFormat,
    val totalLaps: Int = 20,
    seed: Long = 42L,
    val track: MockTrack = MockTrack(),
    val mode: MockSessionMode = MockSessionMode.RACE,
) {
    val entrants: List<MockEntrant> = MockRoster.forFormat(format)
    val carCount = entrants.size
    val cars = MockCars(carCount)
    val events = MockEvents()
    internal val rng = XorShift(seed)
    private val qualifying = MockQualifyingRules(this)

    /** The human player's vehicle index — deliberately not 0 to catch index mix-ups. */
    val playerIndex = 5

    var sessionTime = 0f; internal set
    var phase = Phase.GRID; internal set
    var lightsShown = 0; private set
    var drsEnabled = false; private set

    /** 0 none, 1 full safety car (mirrors Session m_safetyCarStatus). */
    var safetyCarStatus = 0; private set
    var sessionBestLapMs = Long.MAX_VALUE; internal set
    var fastestLapCar = -1; internal set
    var finishedAt = -1f; internal set

    /** Session length in seconds (the game's m_sessionDuration). */
    val sessionDurationS: Int = if (mode == MockSessionMode.QUALIFYING) 720 else 7200

    private var lightsOutAt = 0f
    // Scripted incidents scale with race length so short test races still see all of them.
    private val safetyCarLap = (totalLaps * 0.35f).toInt().coerceAtLeast(2) + rng.nextInt(2)
    private var safetyCarEndLap = -1
    private var safetyCarReturning = false
    private var drsReenableLap = 3
    private val penaltyLap = (totalLaps / 5).coerceAtLeast(2)
    private var penaltyIssued = false
    private val retirementLap = (totalLaps / 2).coerceAtLeast(3)
    private var retiredCar = -1
    private var finishers = 0
    private var playerContact = false

    enum class Phase { GRID, LIGHTS, RACING, FINISHED }

    init {
        reset()
    }

    fun reset() {
        sessionTime = 0f
        phase = Phase.GRID
        lightsShown = 0
        drsEnabled = false
        safetyCarStatus = 0
        sessionBestLapMs = Long.MAX_VALUE
        fastestLapCar = -1
        finishedAt = -1f
        safetyCarEndLap = -1
        safetyCarReturning = false
        drsReenableLap = 3
        penaltyIssued = false
        retiredCar = -1
        finishers = 0
        events.clear()
        val c = cars
        for (i in 0 until carCount) {
            c.order[i] = i
            c.lap[i] = 1
            c.lapDistance[i] = 0f
            c.totalDistance[i] = c.lapDistance[i]
            c.rankKey[i] = c.totalDistance[i]
            c.speed[i] = 0f
            c.accel[i] = 0f
            c.lapStartTime[i] = 0f
            c.lastLapMs[i] = 0
            c.bestLapMs[i] = 0
            c.sector[i] = 0
            c.sector1Ms[i] = 0
            c.sector2Ms[i] = 0
            c.gapToFrontMs[i] = 0
            c.gapToLeaderMs[i] = 0
            c.pitStatus[i] = 0
            c.pitStops[i] = 0
            c.plannedPitLap[i] = if (i == playerIndex) totalLaps / 2 else 5 + rng.nextInt(max(1, totalLaps - 9))
            c.pitStopRemaining[i] = 0f
            c.pitStopTimerMs[i] = 0
            c.pitLaneTimeMs[i] = 0
            val startOnSoft = i % 3 != 2
            c.visualCompound[i] = if (startOnSoft) VISUAL_SOFT else VISUAL_MEDIUM
            c.actualCompound[i] = if (startOnSoft) ACTUAL_C3 else ACTUAL_C2
            c.tyreAge[i] = if (i % 4 == 0) 2 else 0
            c.tyreWear[i] = c.tyreAge[i] * 0.02f
            c.fuel[i] = FUEL_PER_LAP_KG * totalLaps + 1.5f
            c.ersStore[i] = ERS_MAX_J
            c.ersHarvestedLap[i] = 0f
            c.ersDeployedLap[i] = 0f
            c.brakeTemp[i] = 350f
            c.resultStatus[i] = RESULT_ACTIVE
            c.penaltiesSeconds[i] = 0
            c.warnings[i] = 0
            c.drsOpen[i] = false
            c.drsAllowed[i] = false
            c.lapNoise[i] = rng.range(-0.002f, 0.002f)
            c.laneOffset[i] = ((i % 5) - 2) * 1.6f
            c.lastCheckpoint[i] = -1
            c.driverStatus[i] = DRIVER_ON_TRACK
            c.lapInvalid[i] = false
            c.historyLaps[i] = 0
            c.stintCount[i] = 1
            c.stintEndLap[i * MockCars.MAX_STINTS] = 255
            c.stintVisual[i * MockCars.MAX_STINTS] = c.visualCompound[i]
            c.stintActual[i * MockCars.MAX_STINTS] = c.actualCompound[i]

            c.frontLeftWingDamage[i] = 0
            c.floorDamage[i] = 0
            c.engineWear[i] = 6f + rng.range(0f, 8f)
        }
        playerContact = false
        buildGrid()
        if (mode == MockSessionMode.QUALIFYING) qualifying.reset()
        c.checkpointLap.fill(-1)
        events.raise(EventCode.SESSION_STARTED)
    }

    /** Grid roughly in pace order, with enough noise that faster cars start behind slower ones. */
    private fun buildGrid() {
        val c = cars
        val keys = FloatArray(carCount) { it + rng.range(-5f, 5f) }
        for (a in 1 until carCount) {
            val car = c.order[a]
            var b = a - 1
            while (b >= 0 && keys[c.order[b]] > keys[car]) {
                c.order[b + 1] = c.order[b]
                b--
            }
            c.order[b + 1] = car
        }
        for (k in 0 until carCount) {
            val i = c.order[k]
            c.gridPosition[i] = k + 1
            c.position[i] = k + 1
            c.previousPosition[i] = k + 1
            c.lapDistance[i] = -GRID_SPACING_M * (k + 1)
            c.totalDistance[i] = c.lapDistance[i]
            c.rankKey[i] = c.totalDistance[i]
            c.lapStartPosition[i * MockCars.MAX_LAPS] = k + 1
        }
    }

    fun step(dt: Float) {
        sessionTime += dt
        if (mode == MockSessionMode.QUALIFYING) {
            qualifying.step(dt)
            return
        }
        when (phase) {
            Phase.GRID -> if (sessionTime >= GRID_WAIT_S) phase = Phase.LIGHTS
            Phase.LIGHTS -> runLights()
            Phase.RACING, Phase.FINISHED -> {
                moveCars(dt)
                sortOrder()
                raceControl()
            }
        }
    }

    // ---------------------------------------------------------------- start

    private fun runLights() {
        val wanted = min(5, ((sessionTime - GRID_WAIT_S) / 1f).toInt() + 1)
        while (lightsShown < wanted) {
            lightsShown++
            events.raise(EventCode.START_LIGHTS).numLights = lightsShown
            if (lightsShown == 5) lightsOutAt = sessionTime + 1f + rng.range(0.2f, 1.5f)
        }
        if (lightsShown == 5 && sessionTime >= lightsOutAt) {
            events.raise(EventCode.LIGHTS_OUT)
            phase = Phase.RACING
            for (i in 0 until carCount) cars.lapStartTime[i] = sessionTime
        }
    }

    // ---------------------------------------------------------------- motion

    private fun moveCars(dt: Float) {
        val c = cars
        val length = track.lengthM
        for (k in 0 until carCount) {
            val i = c.order[k]
            if (c.resultStatus[i] == RESULT_RETIRED) {
                c.accel[i] = (0f - c.speed[i]) / dt
                c.speed[i] = max(0f, c.speed[i] - 25f * dt)
                continue
            }
            val s = c.lapDistance[i]
            var target = track.speedAt(s) * paceFactor(i)
            if (safetyCarStatus != 0 || c.resultStatus[i] == RESULT_FINISHED) {
                target = min(target, track.speedAt(s) * SAFETY_CAR_PACE)
            }
            if (safetyCarStatus != 0 && k > 0) {
                // Bunch up behind the car ahead.
                val gapM = c.totalDistance[c.order[k - 1]] - c.totalDistance[i]
                target *= 1f + ((gapM - SC_GAP_M) / 400f).coerceIn(-0.3f, 0.35f)
            }
            target = applyTraffic(i, k, s, target)
            target = applyPitLane(i, s, target, dt)
            val v = if (c.pitStatus[i] == PIT_STATIONARY) 0f else target
            c.accel[i] = (v - c.speed[i]) / dt
            c.speed[i] = v
            val ds = v * dt
            c.lapDistance[i] = s + ds
            c.totalDistance[i] += ds
            if (c.resultStatus[i] == RESULT_ACTIVE) c.rankKey[i] = c.totalDistance[i]
            updateEnergy(i, ds, dt)
            updateTiming(i, ds, dt)
            if (c.lapDistance[i] >= length) completeLap(i, v)
        }
    }

    internal fun paceFactor(i: Int): Float {
        val c = cars
        val wearPenalty = c.tyreWear[i] * 0.06f
        val compound = if (c.visualCompound[i] == VISUAL_SOFT) 0.004f else if (c.visualCompound[i] == VISUAL_HARD) -0.004f else 0f
        val fuelBonus = (FUEL_PER_LAP_KG * totalLaps - c.fuel[i]) * 0.00012f
        return entrants[i].pace * (1f + compound + fuelBonus + c.lapNoise[i] - wearPenalty) * 0.985f
    }

    /** Dirty air: close behind another car you can only pass with DRS or a clear pace advantage. */
    private fun applyTraffic(i: Int, k: Int, s: Float, target: Float): Float {
        val c = cars
        c.drsOpen[i] = false
        if (k == 0 || safetyCarStatus != 0) {
            c.drsAllowed[i] = false
            return target
        }
        val ahead = c.order[k - 1]
        if (c.resultStatus[ahead] != RESULT_ACTIVE || c.pitStatus[ahead] != 0 || c.pitStatus[i] != 0) return target
        val gapM = c.totalDistance[ahead] - c.totalDistance[i]
        val gapS = if (c.speed[i] > 1f) gapM / c.speed[i] else Float.MAX_VALUE
        c.drsAllowed[i] = drsEnabled && gapS < 1f
        if (c.drsAllowed[i] && track.inDrsZone(s)) {
            c.drsOpen[i] = true
            return target * DRS_BOOST
        }
        // In dirty air you can match the car ahead; passing needs DRS or a clear pace advantage.
        if (gapM in 0f..FOLLOW_DISTANCE_M) {
            return if (target > c.speed[ahead] * CLEAR_PACE_ADVANTAGE) target * DIRTY_AIR_LOSS else min(target, c.speed[ahead])
        }
        return target
    }

    private fun applyPitLane(i: Int, s: Float, target: Float, dt: Float): Float {
        val c = cars
        val length = track.lengthM
        val onPitLap = c.lap[i] == c.plannedPitLap[i] && c.pitStops[i] == 0
        when (c.pitStatus[i]) {
            0 -> if (onPitLap && s > length - PIT_ENTRY_M && c.resultStatus[i] == RESULT_ACTIVE) {
                c.pitStatus[i] = PIT_LANE
                c.pitLaneTimeMs[i] = 0
                if (i != playerIndex && entrants[i].teamId == entrants[playerIndex].teamId) {
                    events.raise(EventCode.TEAM_MATE_IN_PITS).vehicleIdx = i
                }
            }
            PIT_LANE -> {
                c.pitLaneTimeMs[i] += (dt * 1000).toInt()
                if (c.pitStops[i] > 0 && s in PIT_EXIT_M..(length - PIT_ENTRY_M)) c.pitStatus[i] = 0
            }
            PIT_STATIONARY -> {
                c.pitLaneTimeMs[i] += (dt * 1000).toInt()
                c.pitStopTimerMs[i] += (dt * 1000).toInt()
                c.pitStopRemaining[i] -= dt
                if (c.pitStopRemaining[i] <= 0f) {
                    c.pitStatus[i] = PIT_LANE
                    c.pitStops[i]++
                    val toHard = c.visualCompound[i] != VISUAL_HARD
                    changeTyres(i, if (toHard) VISUAL_HARD else VISUAL_MEDIUM, if (toHard) ACTUAL_C1 else ACTUAL_C2)
                }
            }
        }
        if (c.pitStatus[i] == PIT_LANE && c.pitStops[i] == 0 && s >= 0f && s < PIT_BOX_M && c.lap[i] > c.plannedPitLap[i]) {
            c.pitStatus[i] = PIT_STATIONARY
            c.pitStopRemaining[i] = rng.range(2.1f, 3.4f)
            c.pitStopTimerMs[i] = 0
        }
        return if (c.pitStatus[i] != 0) min(target, PIT_SPEED_MS) else target
    }

    internal fun updateEnergy(i: Int, ds: Float, dt: Float) {
        val c = cars
        val lapFraction = ds / track.lengthM
        c.fuel[i] = max(0f, c.fuel[i] - lapFraction * FUEL_PER_LAP_KG)
        val wearPerLap = when (c.visualCompound[i]) {
            VISUAL_SOFT -> 0.034f
            VISUAL_MEDIUM -> 0.022f
            else -> 0.015f
        }
        c.tyreWear[i] = min(1f, c.tyreWear[i] + lapFraction * wearPerLap)
        c.engineWear[i] = min(100f, c.engineWear[i] + lapFraction * 0.35f)
        val a = c.accel[i]
        if (a < -8f) {
            val harvest = min(ERS_HARVEST_W * dt, ERS_MAX_J - c.ersStore[i])
            c.ersStore[i] += harvest
            c.ersHarvestedLap[i] += harvest
            c.brakeTemp[i] = min(1050f, c.brakeTemp[i] + (-a) * dt * 14f)
        } else {
            c.brakeTemp[i] -= (c.brakeTemp[i] - 320f) * 0.35f * dt
        }
        if (c.speed[i] > ERS_DEPLOY_ABOVE_MS && safetyCarStatus == 0 && c.ersStore[i] > 0f) {
            val deploy = min(ERS_DEPLOY_W * dt, c.ersStore[i])
            c.ersStore[i] -= deploy
            c.ersDeployedLap[i] += deploy
        }
    }

    internal fun updateTiming(i: Int, ds: Float, dt: Float) {
        val c = cars
        val s = c.lapDistance[i]
        val lapElapsedMs = ((sessionTime - c.lapStartTime[i]) * 1000).toInt()
        if (c.sector[i] == 0 && s >= track.sector2Start) {
            c.sector1Ms[i] = lapElapsedMs
            c.sector[i] = 1
        } else if (c.sector[i] == 1 && s >= track.sector3Start) {
            c.sector2Ms[i] = lapElapsedMs - c.sector1Ms[i]
            c.sector[i] = 2
        }
        if (s < 0f) return
        val cp = ((s / track.lengthM) * MockCars.CHECKPOINTS).toInt().coerceIn(0, MockCars.CHECKPOINTS - 1)
        if (cp == c.lastCheckpoint[i]) return
        c.lastCheckpoint[i] = cp
        val slot = c.checkpointSlot(i, c.lap[i], cp)
        c.checkpointTime[slot] = sessionTime
        c.checkpointLap[slot] = c.lap[i]
        updateGaps(i, cp)
    }

    /** Interval timing: compare when this car and the car ahead crossed the same checkpoint. */
    private fun updateGaps(i: Int, cp: Int) {
        val c = cars
        val pos = c.position[i]
        if (pos <= 1) {
            c.gapToFrontMs[i] = 0
            c.gapToLeaderMs[i] = 0
            return
        }
        c.gapToFrontMs[i] = gapTo(i, c.order[pos - 2], cp)
        c.gapToLeaderMs[i] = gapTo(i, c.order[0], cp)
    }

    private fun gapTo(i: Int, other: Int, cp: Int): Int {
        val c = cars
        val lap = c.lap[i]
        val slot = c.checkpointSlot(other, lap, cp)
        if (c.checkpointLap[slot] == lap) {
            return ((sessionTime - c.checkpointTime[slot]) * 1000).toInt().coerceAtLeast(0)
        }
        val gapM = c.totalDistance[other] - c.totalDistance[i]
        return if (c.speed[i] > 1f) (gapM / c.speed[i] * 1000).toInt().coerceAtLeast(0) else 0
    }

    internal fun completeLap(i: Int, v: Float) {
        val c = cars
        c.lapDistance[i] -= track.lengthM
        val overshoot = if (v > 0f) c.lapDistance[i] / v else 0f
        val crossTime = sessionTime - overshoot
        val lapMs = ((crossTime - c.lapStartTime[i]) * 1000).toLong()
        c.lastLapMs[i] = lapMs
        recordHistory(i, lapMs)
        val countsAsBest = !c.lapInvalid[i] && (mode == MockSessionMode.RACE || c.driverStatus[i] == DRIVER_FLYING)
        if (countsAsBest && (c.bestLapMs[i] == 0L || lapMs < c.bestLapMs[i])) c.bestLapMs[i] = lapMs
        if (countsAsBest && lapMs < sessionBestLapMs && c.pitStatus[i] == 0) {
            sessionBestLapMs = lapMs
            fastestLapCar = i
            val e = events.raise(EventCode.FASTEST_LAP)
            e.vehicleIdx = i
            e.lapTimeSeconds = lapMs / 1000f
        }
        c.lapStartTime[i] = crossTime
        c.sector[i] = 0
        c.sector1Ms[i] = 0
        c.sector2Ms[i] = 0
        c.tyreAge[i]++
        c.ersHarvestedLap[i] = 0f
        c.ersDeployedLap[i] = 0f
        c.lapNoise[i] = rng.range(-0.002f, 0.002f)
        c.lapInvalid[i] = false

        if (mode == MockSessionMode.QUALIFYING) {
            c.lap[i]++
            qualifying.onLapCompleted(i)
            return
        }
        if (phase == Phase.FINISHED && c.resultStatus[i] == RESULT_ACTIVE) {
            finishCar(i)
            return
        }
        if (c.resultStatus[i] != RESULT_ACTIVE) return
        if (c.lap[i] == totalLaps && c.position[i] == 1) {
            phase = Phase.FINISHED
            events.raise(EventCode.CHEQUERED_FLAG)
            events.raise(EventCode.RACE_WINNER).vehicleIdx = i
            finishCar(i)
            return
        }
        c.lap[i]++
        if (c.lap[i] <= MockCars.MAX_LAPS) c.lapStartPosition[i * MockCars.MAX_LAPS + c.lap[i] - 1] = c.position[i]
    }

    private fun recordHistory(i: Int, lapMs: Long) {
        val c = cars
        val n = c.historyLaps[i]
        if (n >= MockCars.MAX_LAPS) return
        val k = i * MockCars.MAX_LAPS + n
        c.historyLapMs[k] = lapMs
        c.historyS1[k] = c.sector1Ms[i]
        c.historyS2[k] = c.sector2Ms[i]
        c.historyS3[k] = (lapMs - c.sector1Ms[i] - c.sector2Ms[i]).toInt().coerceAtLeast(0)
        c.historyValid[k] = !c.lapInvalid[i]
        c.historyLaps[i] = n + 1
    }

    /** Closes the current tyre stint on the last completed lap and opens a new one. */
    internal fun changeTyres(i: Int, visual: Int, actual: Int) {
        val c = cars
        val base = i * MockCars.MAX_STINTS
        val n = c.stintCount[i]
        if (n > 0) c.stintEndLap[base + n - 1] = (c.lap[i] - 1).coerceAtLeast(1)
        if (n < MockCars.MAX_STINTS) {
            c.stintEndLap[base + n] = 255
            c.stintVisual[base + n] = visual
            c.stintActual[base + n] = actual
            c.stintCount[i] = n + 1
        }
        c.visualCompound[i] = visual
        c.actualCompound[i] = actual
        c.tyreAge[i] = 0
        c.tyreWear[i] = 0f
    }

    internal fun finishCar(i: Int) {
        val c = cars
        c.resultStatus[i] = RESULT_FINISHED
        finishers++
        c.rankKey[i] = 1e9f - finishers
        if (finishers >= activeCount()) {
            finishedAt = sessionTime
            events.raise(EventCode.SESSION_ENDED)
        }
    }

    internal fun activeCount(): Int {
        var n = 0
        for (i in 0 until carCount) if (cars.resultStatus[i] != RESULT_RETIRED) n++
        return n
    }

    // ---------------------------------------------------------------- order & race control

    internal fun sortOrder() {
        val c = cars
        for (i in 0 until carCount) c.previousPosition[i] = c.position[i]
        // Insertion sort — the order is almost always already sorted, so this is ~O(n).
        for (a in 1 until carCount) {
            val car = c.order[a]
            val key = sortKey(car)
            var b = a - 1
            while (b >= 0 && sortKey(c.order[b]) < key) {
                c.order[b + 1] = c.order[b]
                b--
            }
            c.order[b + 1] = car
        }
        for (k in 0 until carCount) c.position[c.order[k]] = k + 1
        if (phase != Phase.RACING) return
        for (k in 0 until carCount - 1) {
            val a = c.order[k]
            val b = c.order[k + 1]
            if (c.previousPosition[a] > c.previousPosition[b] && c.pitStatus[a] == 0 && c.pitStatus[b] == 0 &&
                c.resultStatus[a] == RESULT_ACTIVE && c.resultStatus[b] == RESULT_ACTIVE && c.lap[a] > 1
            ) {
                val e = events.raise(EventCode.OVERTAKE)
                e.overtakingVehicleIdx = a
                e.beingOvertakenVehicleIdx = b
            }
        }
    }

    private fun sortKey(car: Int): Float =
        if (cars.resultStatus[car] == RESULT_RETIRED) -1e9f + cars.totalDistance[car] else cars.rankKey[car]

    private fun raceControl() {
        if (phase != Phase.RACING) return
        val leader = cars.order[0]
        val leaderLap = cars.lap[leader]
        val leaderS = cars.lapDistance[leader]

        if (!drsEnabled && safetyCarStatus == 0 && leaderLap >= drsReenableLap) {
            drsEnabled = true
            events.raise(EventCode.DRS_ENABLED)
        }
        if (safetyCarStatus == 0 && safetyCarEndLap < 0 && leaderLap == safetyCarLap && leaderS > track.lengthM * 0.4f) {
            safetyCarStatus = 1
            safetyCarEndLap = leaderLap + 2
            val e = events.raise(EventCode.SAFETY_CAR)
            e.safetyCarType = 1
            e.safetyCarEventType = 0
            if (drsEnabled) {
                drsEnabled = false
                events.raise(EventCode.DRS_DISABLED).reason = 1
            }
        }
        if (safetyCarStatus != 0) {
            if (!safetyCarReturning && leaderLap == safetyCarEndLap && leaderS > track.lengthM * 0.6f) {
                safetyCarReturning = true
                val e = events.raise(EventCode.SAFETY_CAR)
                e.safetyCarType = 1
                e.safetyCarEventType = 1
            }
            if (safetyCarReturning && leaderLap > safetyCarEndLap) {
                safetyCarStatus = 0
                drsReenableLap = leaderLap + 2
                val returned = events.raise(EventCode.SAFETY_CAR)
                returned.safetyCarType = 1
                returned.safetyCarEventType = 2
                val resume = events.raise(EventCode.SAFETY_CAR)
                resume.safetyCarType = 1
                resume.safetyCarEventType = 3
            }
        }
        if (!penaltyIssued && leaderLap == penaltyLap && leaderS > track.lengthM * 0.5f) {
            penaltyIssued = true
            val victim = cars.order[carCount / 2]
            cars.penaltiesSeconds[victim] += 5
            cars.warnings[victim]++
            val e = events.raise(EventCode.PENALTY)
            e.penalty.penaltyType = PENALTY_TIME
            e.penalty.infringementType = INFRINGEMENT_CORNER_CUTTING
            e.penalty.vehicleIdx = victim
            e.penalty.otherVehicleIdx = 255
            e.penalty.timeSeconds = 5
            e.penalty.lapNum = cars.lap[victim]
            e.penalty.placesGained = 0
        }
        val p = playerIndex
        if (!playerContact && cars.lap[p] == CONTACT_LAP && cars.lapDistance[p] > track.lengthM * 0.3f && cars.position[p] > 1) {
            playerContact = true
            val other = cars.order[cars.position[p] - 2]
            cars.frontLeftWingDamage[p] = 14
            cars.floorDamage[p] = 6
            val e = events.raise(EventCode.COLLISION)
            e.collisionVehicle1Idx = p
            e.collisionVehicle2Idx = other
            e.collisionSeverity = 0
        }
        if (retiredCar < 0 && leaderLap == retirementLap && leaderS > track.lengthM * 0.3f) {
            retiredCar = cars.order[carCount - 3].let { if (it == playerIndex) cars.order[carCount - 4] else it }
            cars.resultStatus[retiredCar] = RESULT_RETIRED
            cars.pitStatus[retiredCar] = 0
            val e = events.raise(EventCode.RETIREMENT)
            e.vehicleIdx = retiredCar
            e.reason = REASON_MECHANICAL_FAILURE
        }
    }

    companion object {
        const val RESULT_ACTIVE = 2
        const val DRIVER_IN_GARAGE = 0
        const val DRIVER_FLYING = 1
        const val DRIVER_IN_LAP = 2
        const val DRIVER_OUT_LAP = 3
        const val DRIVER_ON_TRACK = 4
        private const val CONTACT_LAP = 3
        const val RESULT_FINISHED = 3
        const val RESULT_RETIRED = 7
        const val PIT_LANE = 1
        const val PIT_STATIONARY = 2
        const val VISUAL_SOFT = 16
        const val VISUAL_MEDIUM = 17
        const val VISUAL_HARD = 18
        const val ACTUAL_C3 = 18
        const val ACTUAL_C2 = 19
        const val ACTUAL_C1 = 20
        const val ERS_MAX_J = 4_000_000f
        const val FUEL_PER_LAP_KG = 1.6f

        internal const val GRID_WAIT_S = 2f
        private const val GRID_SPACING_M = 8f
        private const val SAFETY_CAR_PACE = 0.58f
        private const val SC_GAP_M = 30f
        private const val DRS_BOOST = 1.045f
        private const val FOLLOW_DISTANCE_M = 12f
        private const val CLEAR_PACE_ADVANTAGE = 1.012f
        private const val DIRTY_AIR_LOSS = 0.996f
        private const val PIT_ENTRY_M = 260f
        private const val PIT_EXIT_M = 380f
        private const val PIT_BOX_M = 120f
        private const val PIT_SPEED_MS = 22.2f
        private const val ERS_HARVEST_W = 120_000f
        private const val ERS_DEPLOY_W = 120_000f
        private const val ERS_DEPLOY_ABOVE_MS = 70f
        private const val PENALTY_TIME = 4
        private const val INFRINGEMENT_CORNER_CUTTING = 7
        private const val REASON_MECHANICAL_FAILURE = 8
    }
}
