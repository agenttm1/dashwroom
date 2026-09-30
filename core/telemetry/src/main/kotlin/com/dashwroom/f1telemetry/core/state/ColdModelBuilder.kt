package com.dashwroom.f1telemetry.core.state

import com.dashwroom.f1telemetry.core.model.ChassisState
import com.dashwroom.f1telemetry.core.model.DamageState
import com.dashwroom.f1telemetry.core.model.DriverHistory
import com.dashwroom.f1telemetry.core.model.DriverState
import com.dashwroom.f1telemetry.core.model.DriverStatus
import com.dashwroom.f1telemetry.core.model.FinalResult
import com.dashwroom.f1telemetry.core.model.HistoryState
import com.dashwroom.f1telemetry.core.model.LapRecord
import com.dashwroom.f1telemetry.core.model.ParticipantInfo
import com.dashwroom.f1telemetry.core.model.PitStatus
import com.dashwroom.f1telemetry.core.model.PlayerCarState
import com.dashwroom.f1telemetry.core.model.RaceState
import com.dashwroom.f1telemetry.core.model.ResultStatus
import com.dashwroom.f1telemetry.core.model.SectorTimes
import com.dashwroom.f1telemetry.core.model.SessionBests
import com.dashwroom.f1telemetry.core.model.SessionInfo
import com.dashwroom.f1telemetry.core.model.SessionState
import com.dashwroom.f1telemetry.core.model.TyreSetState
import com.dashwroom.f1telemetry.core.model.TyreStintRecord
import com.dashwroom.f1telemetry.core.model.WeatherSample
import com.dashwroom.f1telemetry.core.model.WheelState
import com.dashwroom.f1telemetry.core.packet.CarDamagePacket
import com.dashwroom.f1telemetry.core.packet.CarStatusPacket
import com.dashwroom.f1telemetry.core.packet.CarTelemetry2Packet
import com.dashwroom.f1telemetry.core.packet.CarTelemetryPacket
import com.dashwroom.f1telemetry.core.packet.FinalClassificationPacket
import com.dashwroom.f1telemetry.core.packet.LapDataPacket
import com.dashwroom.f1telemetry.core.packet.LapPositionsPacket
import com.dashwroom.f1telemetry.core.packet.MotionExPacket
import com.dashwroom.f1telemetry.core.packet.ParticipantsPacket
import com.dashwroom.f1telemetry.core.packet.SessionHistoryPacket
import com.dashwroom.f1telemetry.core.packet.SessionPacket
import com.dashwroom.f1telemetry.core.packet.TyreSetsPacket
import com.dashwroom.f1telemetry.core.parser.PacketParser
import com.dashwroom.f1telemetry.core.protocol.PacketFormat
import com.dashwroom.f1telemetry.core.protocol.PacketId
import com.dashwroom.f1telemetry.core.spec.Appendix
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer

/**
 * Decodes the [ColdStash] with its own parser and builds the immutable models the UI observes.
 * Runs on the ingest thread at most every [PUBLISH_INTERVAL_NANOS] (or immediately for rare
 * structural packets). Only the model groups whose packets changed are rebuilt, and
 * [MutableStateFlow] drops updates that are equal to the current value.
 */
internal class ColdModelBuilder(
    private val stash: ColdStash,
    private val roster: Roster,
    private val cars: CarsLive,
    private val traces: LapTimingTraces,
) {
    private val parser = PacketParser()
    private var lastPublishNanos = 0L
    private val histories = arrayOfNulls<DriverHistory>(PacketFormat.MAX_CARS)

    private val _session = MutableStateFlow(SessionState.Empty)
    val session: StateFlow<SessionState> = _session.asStateFlow()
    private val _race = MutableStateFlow(RaceState())
    val race: StateFlow<RaceState> = _race.asStateFlow()
    private val _player = MutableStateFlow(PlayerCarState())
    val player: StateFlow<PlayerCarState> = _player.asStateFlow()
    private val _history = MutableStateFlow(HistoryState())
    val history: StateFlow<HistoryState> = _history.asStateFlow()

    fun maybePublish(nowNanos: Long, force: Boolean) {
        if (!force && nowNanos - lastPublishNanos < PUBLISH_INTERVAL_NANOS) return
        lastPublishNanos = nowNanos
        val d = stash.dirty
        if (d[PacketId.SESSION] || d[PacketId.PARTICIPANTS]) buildSession()
        if (d[PacketId.SESSION_HISTORY] || d[PacketId.LAP_POSITIONS] || d[PacketId.FINAL_CLASSIFICATION]) buildHistory()
        if (d[PacketId.LAP_DATA] || d[PacketId.PARTICIPANTS] || d[PacketId.CAR_STATUS] || d[PacketId.CAR_TELEMETRY] ||
            d[PacketId.SESSION_HISTORY] || d[PacketId.CAR_TELEMETRY_2]
        ) buildRace()
        if (d[PacketId.CAR_STATUS] || d[PacketId.CAR_TELEMETRY] || d[PacketId.CAR_DAMAGE] ||
            d[PacketId.TYRE_SETS] || d[PacketId.MOTION_EX]
        ) buildPlayer()
        d.fill(false)
    }

    fun reset(sessionUid: Long, format: PacketFormat, playerIndex: Int) {
        histories.fill(null)
        _session.value = SessionState(sessionUid = sessionUid, format = format, playerCarIndex = playerIndex)
        _race.value = RaceState()
        _player.value = PlayerCarState(vehicleIndex = playerIndex)
        _history.value = HistoryState()
    }

    fun clear() {
        histories.fill(null)
        _session.value = SessionState.Empty
        _race.value = RaceState()
        _player.value = PlayerCarState()
        _history.value = HistoryState()
    }

    private inline fun <reified T> decode(id: Int): T? {
        if (!stash.present[id]) return null
        return parser.parse(stash.buffers[id], stash.lengths[id]) as? T
    }

    private inline fun <reified T> decode(buffer: ByteBuffer, length: Int): T? =
        if (length == 0) null else parser.parse(buffer, length) as? T

    // ------------------------------------------------------------------ session

    private fun buildSession() {
        val current = _session.value
        var next = current
        decode<SessionPacket>(PacketId.SESSION)?.let { p ->
            next = next.copy(info = sessionInfo(p), format = p.format, playerCarIndex = p.header.playerCarIndex)
        }
        decode<ParticipantsPacket>(PacketId.PARTICIPANTS)?.let { p ->
            val count = p.numActiveCars.coerceAtMost(p.numCars)
            val list = ArrayList<ParticipantInfo>(count)
            for (i in 0 until count) {
                val c = p.cars[i]
                list += ParticipantInfo(
                    vehicleIndex = i, name = roster.names[i], driverId = c.driverId, teamId = c.teamId,
                    teamName = roster.teamNames[i], raceNumber = c.raceNumber, nationality = c.nationality,
                    aiControlled = c.aiControlled, telemetryPublic = c.yourTelemetryPublic,
                    liveryColour = if (c.numColours > 0) c.liveryColours[0] else null,
                )
            }
            next = next.copy(participants = list.toImmutableList(), numActiveCars = p.numActiveCars)
        }
        if (next != current) _session.value = next
    }

    private fun sessionInfo(p: SessionPacket) = SessionInfo(
        trackId = p.trackId,
        trackName = Appendix.trackName(p.trackId),
        trackLengthM = p.trackLength,
        sessionType = p.sessionType,
        sessionTypeName = Appendix.sessionTypeName(p.sessionType),
        weather = p.weather,
        weatherName = Appendix.weatherName(p.weather),
        trackTemperatureC = p.trackTemperature,
        airTemperatureC = p.airTemperature,
        totalLaps = p.totalLaps,
        sessionTimeLeftS = p.sessionTimeLeft,
        sessionDurationS = p.sessionDuration,
        safetyCarStatus = p.safetyCarStatus,
        formula = p.formula,
        pitSpeedLimitKph = p.pitSpeedLimit,
        gamePaused = p.gamePaused,
        networkGame = p.networkGame,
        pitStopWindowIdealLap = p.pitStopWindowIdealLap,
        pitStopWindowLatestLap = p.pitStopWindowLatestLap,
        pitStopRejoinPosition = p.pitStopRejoinPosition,
        sector2StartM = p.sector2LapDistanceStart,
        sector3StartM = p.sector3LapDistanceStart,
        numSafetyCarPeriods = p.numSafetyCarPeriods,
        numVirtualSafetyCarPeriods = p.numVirtualSafetyCarPeriods,
        numRedFlagPeriods = p.numRedFlagPeriods,
        marshalZoneFlags = List(p.numMarshalZones) { p.marshalZoneFlag[it] }.toImmutableList(),
        forecast = List(p.numWeatherForecastSamples) { s ->
            val w = p.weatherForecast[s]
            WeatherSample(w.timeOffsetMinutes, w.weather, w.trackTemperature, w.airTemperature, w.rainPercentage)
        }.filter { it.timeOffsetMinutes > 0 || it.weather != 0 || it.rainPercentage > 0 }.distinctBy { it.timeOffsetMinutes }.toImmutableList(),
        ruleSet = p.ruleSet,
        gameMode = p.gameMode,
    )

    // ------------------------------------------------------------------ race

    private fun buildRace() {
        // Each packet type decodes into its own reusable instance, so these stay valid together.
        val l = decode<LapDataPacket>(PacketId.LAP_DATA) ?: return
        val participants = decode<ParticipantsPacket>(PacketId.PARTICIPANTS)
        val status = decode<CarStatusPacket>(PacketId.CAR_STATUS)
        val telemetry = decode<CarTelemetryPacket>(PacketId.CAR_TELEMETRY)
        val t2 = decode<CarTelemetry2Packet>(PacketId.CAR_TELEMETRY_2)
        val numActive = participants?.numActiveCars ?: 0
        val player = l.header.playerCarIndex
        var leaderIdx = -1
        for (i in 0 until l.numCars) if (l.cars[i].carPosition == 1 && l.cars[i].resultStatus >= RESULT_ACTIVE) leaderIdx = i
        val leaderDistance = if (leaderIdx >= 0) l.cars[leaderIdx].totalDistance else 0f
        val trackLength = cars.trackLengthM

        val drivers = ArrayList<DriverState>(l.numCars)
        for (i in 0 until l.numCars) {
            val c = l.cars[i]
            if (c.resultStatus < RESULT_ACTIVE && i >= numActive) continue
            if (c.resultStatus == 0 && c.carPosition == 0) continue
            val history = histories[i]
            val bestFromHistory = history?.bestLap?.lapTimeMs ?: 0L
            val lastRecord = history?.laps?.lastOrNull()
            val bestSectors = history?.let { h ->
                val s1 = h.laps.firstOrNull { it.lap == h.bestSector1LapNumber }?.sectorsMs?.s1Ms ?: 0
                val s2 = h.laps.firstOrNull { it.lap == h.bestSector2LapNumber }?.sectorsMs?.s2Ms ?: 0
                val s3 = h.laps.firstOrNull { it.lap == h.bestSector3LapNumber }?.sectorsMs?.s3Ms ?: 0
                if (s1 > 0 || s2 > 0 || s3 > 0) SectorTimes(s1, s2, s3) else null
            }
            val behind = if (trackLength > 0f && leaderIdx >= 0 && i != leaderIdx) {
                ((leaderDistance - c.totalDistance) / trackLength).toInt().coerceAtLeast(0)
            } else 0
            drivers += DriverState(
                vehicleIndex = i,
                name = roster.names[i],
                code = roster.codes[i],
                teamId = roster.teamIds[i],
                teamName = roster.teamNames[i],
                liveryColour = participants?.cars?.get(i)?.takeIf { it.numColours > 0 }?.liveryColours?.get(0),
                raceNumber = participants?.cars?.get(i)?.raceNumber ?: 0,
                isPlayer = i == player,
                aiControlled = participants?.cars?.get(i)?.aiControlled ?: true,
                telemetryPublic = participants?.cars?.get(i)?.yourTelemetryPublic ?: false,
                position = c.carPosition,
                gridPosition = c.gridPosition,
                currentLap = c.currentLapNum,
                lapDistance = c.lapDistance,
                totalDistance = c.totalDistance,
                currentLapTimeMs = c.currentLapTimeMs,
                lastLapTimeMs = c.lastLapTimeMs,
                bestLapTimeMs = if (bestFromHistory > 0) bestFromHistory else traces.bestLapMs(i),
                sector = c.sector,
                currentSector1Ms = c.sector1TimeMs,
                currentSector2Ms = c.sector2TimeMs,
                lastSectorsMs = lastRecord?.sectorsMs,
                bestSectorsMs = bestSectors,
                liveDeltaToBestMs = traces.liveDeltaToBest(i),
                intervalMs = c.deltaToCarInFrontMs,
                gapToLeaderMs = c.deltaToRaceLeaderMs,
                lapsBehindLeader = behind,
                pitStatus = PitStatus.entries.getOrElse(c.pitStatus) { PitStatus.NONE },
                numPitStops = c.numPitStops,
                penaltiesSeconds = c.penaltiesSeconds,
                totalWarnings = c.totalWarnings,
                unservedDriveThroughs = c.numUnservedDriveThroughPens,
                unservedStopGoes = c.numUnservedStopGoPens,
                driverStatus = DriverStatus.entries.getOrElse(c.driverStatus) { DriverStatus.ON_TRACK },
                resultStatus = ResultStatus.entries.getOrElse(c.resultStatus) { ResultStatus.INVALID },
                currentLapInvalid = c.currentLapInvalid,
                tyreVisual = status?.cars?.get(i)?.visualTyreCompound ?: 0,
                tyreActual = status?.cars?.get(i)?.actualTyreCompound ?: 0,
                tyreAgeLaps = status?.cars?.get(i)?.tyresAgeLaps ?: 0,
                drsOpen = telemetry?.cars?.get(i)?.drs ?: false,
                drsAllowed = status?.cars?.get(i)?.drsAllowed ?: false,
                fiaFlag = status?.cars?.get(i)?.vehicleFiaFlags ?: -1,
                overtakeActive = t2?.cars?.get(i)?.overtakeActive ?: false,
            )
        }
        drivers.sortWith(DRIVER_ORDER)
        val next = RaceState(
            drivers = drivers.toImmutableList(),
            bests = bests(drivers),
            leaderLap = if (leaderIdx >= 0) l.cars[leaderIdx].currentLapNum else 0,
            observedPitLaneTimeMs = cars.medianPitLaneTimeMs(),
        )
        if (next != _race.value) _race.value = next
    }

    private fun bests(drivers: List<DriverState>): SessionBests {
        var b = SessionBests()
        for (d in drivers) {
            if (d.bestLapTimeMs > 0 && (b.lapMs == 0L || d.bestLapTimeMs < b.lapMs)) b = b.copy(lapMs = d.bestLapTimeMs, lapVehicle = d.vehicleIndex)
            val s = d.bestSectorsMs ?: continue
            if (s.s1Ms > 0 && (b.s1Ms == 0 || s.s1Ms < b.s1Ms)) b = b.copy(s1Ms = s.s1Ms, s1Vehicle = d.vehicleIndex)
            if (s.s2Ms > 0 && (b.s2Ms == 0 || s.s2Ms < b.s2Ms)) b = b.copy(s2Ms = s.s2Ms, s2Vehicle = d.vehicleIndex)
            if (s.s3Ms > 0 && (b.s3Ms == 0 || s.s3Ms < b.s3Ms)) b = b.copy(s3Ms = s.s3Ms, s3Vehicle = d.vehicleIndex)
        }
        return b
    }

    // ------------------------------------------------------------------ player

    private fun buildPlayer() {
        val player = _session.value.playerCarIndex
        var next = _player.value.copy(vehicleIndex = player)
        val tempsSurface = IntArray(4); val tempsInner = IntArray(4); val pressure = FloatArray(4)
        val brakeTemp = IntArray(4); val surface = IntArray(4)
        var engineTemp = next.engineTemperatureC
        decode<CarTelemetryPacket>(PacketId.CAR_TELEMETRY)?.let { t ->
            if (player < t.numCars) {
                val c = t.cars[player]
                for (w in 0 until 4) {
                    tempsSurface[w] = c.tyresSurfaceTemperature[w]; tempsInner[w] = c.tyresInnerTemperature[w]
                    pressure[w] = c.tyresPressure[w]; brakeTemp[w] = c.brakesTemperature[w]; surface[w] = c.surfaceType[w]
                }
                engineTemp = c.engineTemperature
                next = next.copy(hasTelemetry = true)
            }
        }
        val wear = FloatArray(4); val tyreDamage = IntArray(4); val blisters = IntArray(4); val brakeDamage = IntArray(4)
        decode<CarDamagePacket>(PacketId.CAR_DAMAGE)?.let { dmg ->
            if (player < dmg.numCars) {
                val c = dmg.cars[player]
                for (w in 0 until 4) {
                    wear[w] = c.tyresWear[w]; tyreDamage[w] = c.tyresDamage[w]
                    blisters[w] = c.tyreBlisters[w]; brakeDamage[w] = c.brakesDamage[w]
                }
                next = next.copy(
                    hasDamage = true,
                    damage = DamageState(
                        frontLeftWing = c.frontLeftWingDamage, frontRightWing = c.frontRightWingDamage,
                        rearWing = c.rearWingDamage, floor = c.floorDamage, diffuser = c.diffuserDamage,
                        sidepod = c.sidepodDamage, gearbox = c.gearBoxDamage, engine = c.engineDamage,
                        drsFault = c.drsFault, ersFault = c.ersFault, mguhWear = c.engineMguhWear,
                        esWear = c.engineEsWear, ceWear = c.engineCeWear, iceWear = c.engineIceWear,
                        mgukWear = c.engineMgukWear, tcWear = c.engineTcWear, engineBlown = c.engineBlown,
                        engineSeized = c.engineSeized,
                    ),
                )
            }
        }
        val wheels = List(4) { w ->
            WheelState(
                surfaceTempC = tempsSurface[w], innerTempC = tempsInner[w], pressurePsi = pressure[w],
                brakeTempC = brakeTemp[w], wearPercent = wear[w], damagePercent = tyreDamage[w],
                blistersPercent = blisters[w], brakeDamagePercent = brakeDamage[w], surfaceType = surface[w],
            )
        }.toImmutableList()
        next = next.copy(wheels = wheels, engineTemperatureC = engineTemp)
        decode<CarStatusPacket>(PacketId.CAR_STATUS)?.let { s ->
            if (player < s.numCars) {
                val c = s.cars[player]
                next = next.copy(
                    fuelInTankKg = c.fuelInTank, fuelCapacityKg = c.fuelCapacity, fuelRemainingLaps = c.fuelRemainingLaps,
                    fuelMix = c.fuelMix, ersStoreJ = c.ersStoreEnergy, ersDeployMode = c.ersDeployMode,
                    ersHarvestedThisLapJ = c.ersHarvestedThisLapMguk + c.ersHarvestedThisLapMguh,
                    ersDeployedThisLapJ = c.ersDeployedThisLap,
                    ersHarvestLimitJ = c.ersHarvestLimitPerLap.takeUnless { it.isNaN() },
                    tyreVisual = c.visualTyreCompound, tyreActual = c.actualTyreCompound, tyreAgeLaps = c.tyresAgeLaps,
                    frontBrakeBias = c.frontBrakeBias, tractionControl = c.tractionControl,
                    antiLockBrakes = c.antiLockBrakes, pitLimiterOn = c.pitLimiterOn,
                )
            }
        }
        if (player < PacketFormat.MAX_CARS) {
            decode<TyreSetsPacket>(stash.tyreSetBuffers[player], stash.tyreSetLengths[player])?.let { t ->
                next = next.copy(
                    tyreSets = List(TyreSetsPacket.MAX_SETS) { k ->
                        val s = t.sets[k]
                        TyreSetState(k, s.actualCompound, s.visualCompound, s.wearPercent, s.available,
                            s.recommendedSession, s.lifeSpanLaps, s.usableLifeLaps, s.lapDeltaTimeMs, s.fitted)
                    }.filter { it.visualCompound != 0 }.toImmutableList(),
                    fittedTyreSetIndex = t.fittedIdx,
                )
            }
        }
        decode<MotionExPacket>(PacketId.MOTION_EX)?.let { m ->
            next = next.copy(
                chassis = ChassisState(
                    frontAeroHeightMm = m.frontAeroHeight * 1000f,
                    rearAeroHeightMm = m.rearAeroHeight * 1000f,
                    wheelSlipRatio = m.wheelSlipRatio.toList().toImmutableList(),
                    wheelSlipAngleDeg = m.wheelSlipAngle.map { Math.toDegrees(it.toDouble()).toFloat() }.toImmutableList(),
                    suspensionPositionMm = m.suspensionPosition.toList().toImmutableList(),
                ),
            )
        }
        if (next != _player.value) _player.value = next
    }

    // ------------------------------------------------------------------ history

    private fun buildHistory() {
        var changed = false
        for (car in 0 until PacketFormat.MAX_CARS) {
            if (!stash.historyDirty[car]) continue
            stash.historyDirty[car] = false
            val p = decode<SessionHistoryPacket>(stash.historyBuffers[car], stash.historyLengths[car]) ?: continue
            histories[p.carIdx.coerceIn(0, PacketFormat.MAX_CARS - 1)] = driverHistory(p)
            changed = true
        }
        val current = _history.value
        var next = current
        if (changed) {
            next = next.copy(drivers = histories.withIndex().mapNotNull { (i, h) -> h?.let { i to it } }.toMap().toImmutableMap())
        }
        if (stash.dirty[PacketId.LAP_POSITIONS]) decode<LapPositionsPacket>(PacketId.LAP_POSITIONS)?.let { p ->
            val map = HashMap<Int, ImmutableList<Int>>()
            for (car in 0 until p.numCars) {
                val lead = List(p.lapStart) { 0 }
                val laps = List(p.numLaps) { p.position(it, car) }
                if (laps.any { it > 0 }) map[car] = (lead + laps).toImmutableList()
            }
            next = next.copy(lapPositions = map.toImmutableMap())
        }
        if (stash.dirty[PacketId.FINAL_CLASSIFICATION]) decode<FinalClassificationPacket>(PacketId.FINAL_CLASSIFICATION)?.let { p ->
            next = next.copy(
                finalClassification = (0 until p.numCars)
                    .filter { p.cars[it].position > 0 }
                    .map { i ->
                        val r = p.cars[i]
                        FinalResult(
                            vehicleIndex = i, position = r.position, numLaps = r.numLaps, gridPosition = r.gridPosition,
                            points = r.points, numPitStops = r.numPitStops,
                            resultStatus = ResultStatus.entries.getOrElse(r.resultStatus) { ResultStatus.INVALID },
                            bestLapTimeMs = r.bestLapTimeMs, totalRaceTimeSeconds = r.totalRaceTimeSeconds,
                            penaltiesTimeSeconds = r.penaltiesTimeSeconds,
                            stints = List(r.numTyreStints) { s ->
                                TyreStintRecord(r.tyreStintsEndLaps[s].takeIf { it != 255 }, r.tyreStintsActual[s], r.tyreStintsVisual[s])
                            }.toImmutableList(),
                        )
                    }
                    .sortedBy { it.position }
                    .toImmutableList(),
            )
        }
        if (next != current) _history.value = next
    }

    private fun driverHistory(p: SessionHistoryPacket): DriverHistory {
        val stints = List(p.numTyreStints) { s ->
            val st = p.stints[s]
            TyreStintRecord(st.endLap.takeIf { it != 255 }, st.actualCompound, st.visualCompound)
        }
        val laps = ArrayList<LapRecord>(p.numLaps)
        for (l in 0 until p.numLaps) {
            val h = p.laps[l]
            if (h.lapTimeMs <= 0) continue
            val lapNumber = l + 1
            val stint = stints.firstOrNull { (it.endLap ?: Int.MAX_VALUE) >= lapNumber }
            laps += LapRecord(
                lap = lapNumber,
                lapTimeMs = h.lapTimeMs,
                sectorsMs = SectorTimes(h.sector1Ms, h.sector2Ms, h.sector3Ms),
                valid = h.validFlags and 0x01 != 0,
                s1Valid = h.validFlags and 0x02 != 0,
                s2Valid = h.validFlags and 0x04 != 0,
                s3Valid = h.validFlags and 0x08 != 0,
                tyreVisual = stint?.visualCompound ?: 0,
            )
        }
        return DriverHistory(
            vehicleIndex = p.carIdx,
            laps = laps.toImmutableList(),
            stints = stints.toImmutableList(),
            bestLapNumber = p.bestLapTimeLapNum,
            bestSector1LapNumber = p.bestSector1LapNum,
            bestSector2LapNumber = p.bestSector2LapNum,
            bestSector3LapNumber = p.bestSector3LapNum,
        )
    }

    private companion object {
        const val PUBLISH_INTERVAL_NANOS = 100_000_000L // 10 Hz
        const val RESULT_ACTIVE = 2

        /** Classified/running cars by position first; cars without a position go to the bottom. */
        val DRIVER_ORDER = compareBy<DriverState>({ if (it.position <= 0) 1 else 0 }, { it.position }, { it.vehicleIndex })
    }
}
