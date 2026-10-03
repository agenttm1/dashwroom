package com.dashwroom.f1telemetry.replay.mock

import com.dashwroom.f1telemetry.core.packet.CarDamagePacket
import com.dashwroom.f1telemetry.core.packet.CarStatusPacket
import com.dashwroom.f1telemetry.core.packet.CarTelemetry2Packet
import com.dashwroom.f1telemetry.core.packet.FinalClassificationPacket
import com.dashwroom.f1telemetry.core.packet.LapPositionsPacket
import com.dashwroom.f1telemetry.core.packet.MotionExPacket
import com.dashwroom.f1telemetry.core.packet.SessionHistoryPacket
import com.dashwroom.f1telemetry.core.packet.TyreSetsPacket
import com.dashwroom.f1telemetry.core.packet.CarTelemetryPacket
import com.dashwroom.f1telemetry.core.packet.LapDataPacket
import com.dashwroom.f1telemetry.core.packet.MotionPacket
import com.dashwroom.f1telemetry.core.packet.PacketHeader
import com.dashwroom.f1telemetry.core.packet.ParticipantsPacket
import com.dashwroom.f1telemetry.core.packet.SessionPacket
import com.dashwroom.f1telemetry.core.protocol.PacketFormat
import com.dashwroom.f1telemetry.core.protocol.PacketId
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Fills the reusable packet models from [MockRace] state. Allocation-free after construction.
 */
class MockPacketBuilder(private val race: MockRace, private val sessionUid: Long) {
    private val format = race.format
    private val track = race.track
    private val cars = race.cars

    val motion = MotionPacket()
    val lapData = LapDataPacket()
    val telemetry = CarTelemetryPacket()
    val status = CarStatusPacket()
    val session = SessionPacket()
    val participants = ParticipantsPacket()

    var frame = 0L

    fun header(h: PacketHeader, packetId: Int) {
        h.format = format
        h.packetFormat = format.wireValue
        h.gameYear = if (format == PacketFormat.F1_25_SEASON_2026) 26 else 25
        h.gameMajorVersion = 1
        h.gameMinorVersion = if (format == PacketFormat.F1_25_SEASON_2026) 22 else 18
        h.packetVersion = 1
        h.packetId = packetId
        h.sessionUid = sessionUid
        h.sessionTime = race.sessionTime
        h.frameIdentifier = frame
        h.overallFrameIdentifier = frame
        h.playerCarIndex = race.playerIndex
        h.secondaryPlayerCarIndex = 255
    }

    fun buildMotion(): MotionPacket {
        header(motion.header, PacketId.MOTION)
        for (i in 0 until format.maxCars) {
            val m = motion.cars[i]
            if (i >= race.carCount) {
                zeroMotion(m)
                continue
            }
            val idx = track.index(cars.lapDistance[i])
            val h = track.heading[idx]
            val fx = cos(h)
            val fz = sin(h)
            val off = cars.laneOffset[i]
            val v = cars.speed[i]
            // Cars in the garage sit in the pit lane, beside the start line.
            val garageOffset = if (cars.driverStatus[i] == MockRace.DRIVER_IN_GARAGE) PIT_LANE_OFFSET_M else 0f
            m.worldPositionX = track.x[idx] - fz * (off + garageOffset)
            m.worldPositionY = track.y[idx]
            m.worldPositionZ = track.z[idx] + fx * (off + garageOffset)
            m.worldVelocityX = fx * v
            m.worldVelocityY = 0f
            m.worldVelocityZ = fz * v
            m.worldForwardDirX = fx
            m.worldForwardDirY = 0f
            m.worldForwardDirZ = fz
            m.worldRightDirX = fz
            m.worldRightDirY = 0f
            m.worldRightDirZ = -fx
            m.gForceLateral = (v * v * track.signedCurvature[idx] / G).coerceIn(-6.5f, 6.5f)
            m.gForceLongitudinal = (cars.accel[i] / G).coerceIn(-6.5f, 6.5f)
            m.gForceVertical = 1f
            m.yaw = h
            m.pitch = 0f
            m.roll = 0f
        }
        return motion
    }

    fun buildLapData(): LapDataPacket {
        header(lapData.header, PacketId.LAP_DATA)
        for (i in 0 until format.maxCars) {
            val l = lapData.cars[i]
            if (i >= race.carCount) {
                zeroLap(l)
                continue
            }
            val inGarage = cars.driverStatus[i] == MockRace.DRIVER_IN_GARAGE
            l.lastLapTimeMs = cars.lastLapMs[i]
            l.currentLapTimeMs = if (!inGarage && (race.phase == MockRace.Phase.RACING || race.phase == MockRace.Phase.FINISHED)) {
                ((race.sessionTime - cars.lapStartTime[i]) * 1000).toLong().coerceAtLeast(0)
            } else 0L
            l.sector1TimeMs = cars.sector1Ms[i]
            l.sector2TimeMs = cars.sector2Ms[i]
            l.deltaToCarInFrontMs = cars.gapToFrontMs[i].coerceAtMost(MAX_DELTA_MS)
            l.deltaToRaceLeaderMs = cars.gapToLeaderMs[i].coerceAtMost(MAX_DELTA_MS)
            l.lapDistance = cars.lapDistance[i]
            l.totalDistance = cars.totalDistance[i]
            l.safetyCarDelta = if (race.safetyCarStatus != 0) 1.5f else 0f
            l.carPosition = cars.position[i]
            l.currentLapNum = cars.lap[i]
            l.pitStatus = if (inGarage) 2 else cars.pitStatus[i]
            l.numPitStops = cars.pitStops[i]
            l.sector = cars.sector[i]
            l.currentLapInvalid = cars.lapInvalid[i]
            l.penaltiesSeconds = cars.penaltiesSeconds[i]
            l.totalWarnings = cars.warnings[i]
            l.cornerCuttingWarnings = cars.warnings[i]
            l.numUnservedDriveThroughPens = 0
            l.numUnservedStopGoPens = 0
            l.gridPosition = cars.gridPosition[i]
            l.driverStatus = when {
                race.mode == MockSessionMode.QUALIFYING -> cars.driverStatus[i]
                cars.pitStatus[i] != 0 -> DRIVER_IN_LAP
                else -> DRIVER_ON_TRACK
            }
            l.resultStatus = cars.resultStatus[i]
            l.pitLaneTimerActive = cars.pitStatus[i] != 0
            l.pitLaneTimeInLaneMs = cars.pitLaneTimeMs[i].coerceAtMost(65_535)
            l.pitStopTimerMs = cars.pitStopTimerMs[i].coerceAtMost(65_535)
            l.pitStopShouldServePen = false
            l.speedTrapFastestSpeedKph = 0f
            l.speedTrapFastestLap = 255
        }
        lapData.timeTrialPbCarIdx = 255
        lapData.timeTrialRivalCarIdx = 255
        return lapData
    }

    fun buildTelemetry(): CarTelemetryPacket {
        header(telemetry.header, PacketId.CAR_TELEMETRY)
        for (i in 0 until format.maxCars) {
            val t = telemetry.cars[i]
            if (i >= race.carCount) {
                zeroTelemetry(t)
                continue
            }
            val v = cars.speed[i]
            val kph = v * 3.6f
            val a = cars.accel[i]
            val idx = track.index(cars.lapDistance[i])
            t.speedKph = kph.roundToInt()
            if (a < -3f) {
                t.brake = (-a / MockTrack.BRAKING).coerceIn(0.1f, 1f)
                t.throttle = 0f
            } else {
                t.brake = 0f
                t.throttle = if (race.safetyCarStatus != 0) 0.35f else (0.45f + a / 9f + if (v > MockTrack.V_MAX * 0.95f) 1f else 0f).coerceIn(0f, 1f)
            }
            t.steer = (track.signedCurvature[idx] * 28f).coerceIn(-1f, 1f)
            t.clutch = if (kph < 5f && race.phase == MockRace.Phase.LIGHTS) 100 else 0
            val gear = gearFor(kph)
            t.gear = if (race.phase == MockRace.Phase.GRID || race.phase == MockRace.Phase.LIGHTS) 1 else gear
            t.engineRpm = rpmFor(kph, gear)
            t.drs = cars.drsOpen[i]
            val pct = ((t.engineRpm - REV_LIGHTS_START).toFloat() / (MAX_RPM - 200 - REV_LIGHTS_START) * 100f)
                .coerceIn(0f, 100f)
            t.revLightsPercent = pct.roundToInt()
            val leds = (pct / 100f * 15f).roundToInt()
            t.revLightsBitValue = (1 shl leds) - 1
            val bt = cars.brakeTemp[i].roundToInt()
            val wear = cars.tyreWear[i]
            for (w in 0 until 4) {
                val front = w >= 2
                t.brakesTemperature[w] = bt + if (front) 60 else 0
                t.tyresSurfaceTemperature[w] = (88 + abs(t.steer) * 12 + wear * 10 + (if (front) 3 else 0)).roundToInt()
                t.tyresInnerTemperature[w] = (98 + wear * 6).roundToInt()
                t.tyresPressure[w] = (if (front) 23.1f else 21.4f) + wear * 1.2f
                t.surfaceType[w] = 0
            }
            t.engineTemperature = 108 + (cars.accel[i].coerceAtLeast(0f) / 4).roundToInt()
        }
        telemetry.mfdPanelIndex = 255
        telemetry.mfdPanelIndexSecondaryPlayer = 255
        val p = race.playerIndex
        val kph = cars.speed[p] * 3.6f
        telemetry.suggestedGear = gearFor(kph)
        return telemetry
    }

    fun buildStatus(): CarStatusPacket {
        header(status.header, PacketId.CAR_STATUS)
        val lapsLeft = race.totalLaps
        for (i in 0 until format.maxCars) {
            val s = status.cars[i]
            if (i >= race.carCount) {
                zeroStatus(s)
                continue
            }
            s.tractionControl = 0
            s.antiLockBrakes = false
            s.fuelMix = 1
            s.frontBrakeBias = 56
            s.pitLimiterOn = cars.pitStatus[i] != 0
            s.fuelInTank = cars.fuel[i]
            s.fuelCapacity = 110f
            val remainingLaps = lapsLeft - cars.lap[i] + 1 - cars.lapDistance[i].coerceAtLeast(0f) / track.lengthM
            s.fuelRemainingLaps = cars.fuel[i] / MockRace.FUEL_PER_LAP_KG - remainingLaps
            s.maxRpm = MAX_RPM
            s.idleRpm = IDLE_RPM
            s.maxGears = 8
            s.drsAllowed = cars.drsAllowed[i]
            s.drsActivationDistance = if (race.drsEnabled && !cars.drsAllowed[i]) 0 else
                track.distanceToDrsZone(cars.lapDistance[i]).roundToInt().coerceIn(0, 65_535)
            s.actualTyreCompound = cars.actualCompound[i]
            s.visualTyreCompound = cars.visualCompound[i]
            s.tyresAgeLaps = cars.tyreAge[i]
            s.vehicleFiaFlags = race.fiaFlag(i)
            s.enginePowerIce = if (cars.speed[i] > 1f) 560_000f else 0f
            s.enginePowerMguk = if (cars.speed[i] > MockTrack.V_MAX * 0.75f) 120_000f else 0f
            s.ersStoreEnergy = cars.ersStore[i]
            s.ersDeployMode = 1
            s.ersHarvestedThisLapMguk = cars.ersHarvestedLap[i]
            s.ersHarvestedThisLapMguh = 0f
            s.ersHarvestLimitPerLap = if (format == PacketFormat.F1_25_SEASON_2026) 8_500_000f else Float.NaN
            s.ersDeployedThisLap = cars.ersDeployedLap[i]
            s.networkPaused = false
        }
        return status
    }

    fun buildSession(): SessionPacket {
        header(session.header, PacketId.SESSION)
        val p = session
        p.weather = 1
        p.trackTemperature = 34
        p.airTemperature = 24
        p.totalLaps = race.totalLaps
        p.trackLength = track.lengthM.roundToInt()
        p.sessionType = race.mode.sessionType
        p.trackId = TRACK_SILVERSTONE
        p.formula = if (format == PacketFormat.F1_25_SEASON_2026) 13 else 0
        p.sessionDuration = race.sessionDurationS
        p.sessionTimeLeft = (race.sessionDurationS - race.sessionTime.toInt()).coerceAtLeast(0)
        p.pitSpeedLimit = 80
        p.gamePaused = false
        p.isSpectating = false
        p.spectatorCarIndex = 255
        p.sliProNativeSupport = false
        p.numMarshalZones = 6
        for (z in 0 until SessionPacket.MAX_MARSHAL_ZONES) {
            p.marshalZoneStart[z] = if (z < 6) z / 6f else 0f
            p.marshalZoneFlag[z] = if (z < 6) (if (race.safetyCarStatus != 0) FLAG_YELLOW else FLAG_NONE) else 0
        }
        p.safetyCarStatus = race.safetyCarStatus
        p.networkGame = false
        p.numWeatherForecastSamples = 3
        for (s in 0 until SessionPacket.MAX_WEATHER_SAMPLES) {
            val w = p.weatherForecast[s]
            val used = s < 3
            w.sessionType = if (used) SESSION_RACE else 0
            w.timeOffsetMinutes = if (used) s * 15 else 0
            w.weather = if (used) (if (s == 2) 2 else 1) else 0
            w.trackTemperature = if (used) 34 - s else 0
            w.trackTemperatureChange = if (used) 1 else 0
            w.airTemperature = if (used) 24 else 0
            w.airTemperatureChange = if (used) 2 else 0
            w.rainPercentage = if (used) s * 5 else 0
        }
        p.forecastAccuracy = 0
        p.aiDifficulty = 90
        p.seasonLinkIdentifier = 0x5EA50L
        p.weekendLinkIdentifier = 0x3EEC3L
        p.sessionLinkIdentifier = sessionUid and 0xFFFF_FFFFL
        p.pitStopWindowIdealLap = race.totalLaps / 2
        p.pitStopWindowLatestLap = race.totalLaps / 2 + 3
        p.pitStopRejoinPosition = 12
        p.steeringAssist = 0; p.brakingAssist = 0; p.gearboxAssist = 1; p.pitAssist = 0
        p.pitReleaseAssist = 0; p.ersAssist = 0; p.drsAssist = 0; p.dynamicRacingLine = 1
        p.dynamicRacingLineType = 0; p.gameMode = 28; p.ruleSet = 1
        p.timeOfDayMinutes = 15L * 60 + (race.sessionTime / 60).toLong()
        p.sessionLength = 4
        p.speedUnitsLeadPlayer = 1; p.temperatureUnitsLeadPlayer = 0
        p.speedUnitsSecondaryPlayer = 1; p.temperatureUnitsSecondaryPlayer = 0
        p.numSafetyCarPeriods = if (race.safetyCarStatus != 0) 1 else 0
        p.numVirtualSafetyCarPeriods = 0; p.numRedFlagPeriods = 0
        p.equalCarPerformance = 0; p.recoveryMode = 1; p.flashbackLimit = 2; p.surfaceType = 1
        p.lowFuelMode = 0; p.raceStarts = 0; p.tyreTemperature = 1; p.pitLaneTyreSim = 0
        p.carDamage = 2; p.carDamageRate = 1; p.collisions = 2; p.collisionsOffForFirstLapOnly = 0
        p.mpUnsafePitRelease = 0; p.mpOffForGriefing = 0; p.cornerCuttingStringency = 1
        p.parcFermeRules = 1; p.pitStopExperience = 0; p.safetyCar = 2; p.safetyCarExperience = 0
        p.formationLap = 0; p.formationLapExperience = 0; p.redFlags = 2
        p.affectsLicenceLevelSolo = 0; p.affectsLicenceLevelMp = 0
        p.numSessionsInWeekend = 5
        for (s in 0 until SessionPacket.MAX_SESSIONS_IN_WEEKEND) p.weekendStructure[s] = if (s < 5) WEEKEND[s] else 0
        p.sector2LapDistanceStart = track.sector2Start
        p.sector3LapDistanceStart = track.sector3Start
        if (format == PacketFormat.F1_25_SEASON_2026) {
            val zones = track.drsZones.size / 2
            p.activeAeroTrackStatus = 0
            p.numActiveAeroZonesFull = zones
            p.numActiveAeroZonesPartial = 0
            p.numDrsZones = zones
            for (z in 0 until SessionPacket.MAX_ACTIVE_AERO_ZONES) {
                p.activeAeroZonesFullStart[z] = if (z < zones) track.drsZones[z * 2] else 0f
                p.activeAeroZonesFullEnd[z] = if (z < zones) track.drsZones[z * 2 + 1] else 0f
                p.activeAeroZonesPartialStart[z] = 0f
                p.activeAeroZonesPartialEnd[z] = 0f
            }
            for (z in 0 until SessionPacket.MAX_DRS_ZONES) {
                p.drsZoneStart[z] = if (z < zones) track.drsZones[z * 2] else 0f
                p.drsZoneEnd[z] = if (z < zones) track.drsZones[z * 2 + 1] else 0f
            }
            p.startReactionTime = 0.21f
            p.antiLockBrakesAssist = 0; p.tractionControlAssist = 0; p.dynamicRacingLineHiVis = 0
            p.dynamicRacingLineColourBlind = 0; p.recurringRewindPrompt = 0
        }
        return p
    }

    fun buildParticipants(): ParticipantsPacket {
        header(participants.header, PacketId.PARTICIPANTS)
        participants.numActiveCars = race.carCount
        for (i in 0 until format.maxCars) {
            val p = participants.cars[i]
            if (i >= race.carCount) {
                p.aiControlled = false; p.driverId = 0; p.networkId = 0; p.teamId = 0; p.myTeam = false
                p.raceNumber = 0; p.nationality = 0; p.nameLength = 0; p.name = ""
                p.yourTelemetryPublic = false; p.showOnlineNames = false; p.techLevel = 0; p.platform = 255
                p.numColours = 0; p.liveryColours.fill(0)
                continue
            }
            val e = race.entrants[i]
            p.aiControlled = i != race.playerIndex
            p.driverId = e.driverId
            p.networkId = 0
            p.teamId = e.teamId
            p.myTeam = false
            p.raceNumber = e.raceNumber
            p.nationality = e.nationality
            setName(p, e.name)
            p.yourTelemetryPublic = true
            p.showOnlineNames = true
            p.techLevel = 0
            p.platform = 1
            p.numColours = 1
            p.liveryColours[0] = e.livery
            for (k in 1 until ParticipantsPacket.MAX_LIVERY_COLOURS) p.liveryColours[k] = 0
        }
        return participants
    }

    val damage = CarDamagePacket()
    val history = SessionHistoryPacket()
    val tyreSets = TyreSetsPacket()
    val lapPositions = LapPositionsPacket()
    val classification = FinalClassificationPacket()
    val motionEx = MotionExPacket()
    val telemetry2 = CarTelemetry2Packet()

    fun buildDamage(): CarDamagePacket {
        header(damage.header, PacketId.CAR_DAMAGE)
        for (i in 0 until format.maxCars) {
            val d = damage.cars[i]
            val active = i < race.carCount
            val wear = if (active) cars.tyreWear[i] * 100f else 0f
            for (w in 0 until 4) {
                d.tyresWear[w] = wear * WHEEL_WEAR_BIAS[w]
                d.tyresDamage[w] = if (active) (wear * WHEEL_WEAR_BIAS[w] * 0.4f).roundToInt() else 0
                d.brakesDamage[w] = if (active) (cars.lap[i] / 3).coerceAtMost(15) else 0
                d.tyreBlisters[w] = if (active && wear > 60f) ((wear - 60f) * 0.8f).roundToInt() else 0
            }
            d.frontLeftWingDamage = if (active) cars.frontLeftWingDamage[i] else 0
            d.frontRightWingDamage = 0
            d.rearWingDamage = 0
            d.floorDamage = if (active) cars.floorDamage[i] else 0
            d.diffuserDamage = 0
            d.sidepodDamage = 0
            d.drsFault = false
            d.ersFault = false
            d.gearBoxDamage = if (active) (cars.engineWear[i] * 0.6f).roundToInt() else 0
            d.engineDamage = if (active) (cars.engineWear[i] * 0.3f).roundToInt() else 0
            val ew = if (active) cars.engineWear[i] else 0f
            d.engineMguhWear = (ew * 1.1f).roundToInt().coerceAtMost(100)
            d.engineEsWear = (ew * 0.9f).roundToInt().coerceAtMost(100)
            d.engineCeWear = (ew * 0.8f).roundToInt().coerceAtMost(100)
            d.engineIceWear = ew.roundToInt().coerceAtMost(100)
            d.engineMgukWear = (ew * 1.2f).roundToInt().coerceAtMost(100)
            d.engineTcWear = (ew * 0.95f).roundToInt().coerceAtMost(100)
            d.engineBlown = false
            d.engineSeized = false
        }
        return damage
    }

    fun buildHistory(car: Int): SessionHistoryPacket {
        header(history.header, PacketId.SESSION_HISTORY)
        val h = history
        h.carIdx = car
        val completed = cars.historyLaps[car]
        val inProgress = race.phase == MockRace.Phase.RACING && cars.driverStatus[car] != MockRace.DRIVER_IN_GARAGE
        h.numLaps = (completed + if (inProgress) 1 else 0).coerceAtMost(SessionHistoryPacket.MAX_LAPS)
        var best = 0; var bestS1 = 0; var bestS2 = 0; var bestS3 = 0
        for (l in 0 until SessionHistoryPacket.MAX_LAPS) {
            val lap = h.laps[l]
            if (l < completed) {
                val k = car * MockCars.MAX_LAPS + l
                lap.lapTimeMs = cars.historyLapMs[k]
                lap.sector1Ms = cars.historyS1[k]
                lap.sector2Ms = cars.historyS2[k]
                lap.sector3Ms = cars.historyS3[k]
                lap.validFlags = if (cars.historyValid[k]) 0x0F else 0x00
                if (cars.historyValid[k]) {
                    if (best == 0 || lap.lapTimeMs < cars.historyLapMs[car * MockCars.MAX_LAPS + best - 1]) best = l + 1
                    if (bestS1 == 0 || lap.sector1Ms < cars.historyS1[car * MockCars.MAX_LAPS + bestS1 - 1]) bestS1 = l + 1
                    if (bestS2 == 0 || lap.sector2Ms < cars.historyS2[car * MockCars.MAX_LAPS + bestS2 - 1]) bestS2 = l + 1
                    if (bestS3 == 0 || lap.sector3Ms < cars.historyS3[car * MockCars.MAX_LAPS + bestS3 - 1]) bestS3 = l + 1
                }
            } else if (l == completed && inProgress) {
                lap.lapTimeMs = 0
                lap.sector1Ms = cars.sector1Ms[car]
                lap.sector2Ms = cars.sector2Ms[car]
                lap.sector3Ms = 0
                lap.validFlags = if (cars.lapInvalid[car]) 0 else 0x0F
            } else {
                lap.lapTimeMs = 0; lap.sector1Ms = 0; lap.sector2Ms = 0; lap.sector3Ms = 0; lap.validFlags = 0
            }
        }
        h.bestLapTimeLapNum = best
        h.bestSector1LapNum = bestS1
        h.bestSector2LapNum = bestS2
        h.bestSector3LapNum = bestS3
        h.numTyreStints = cars.stintCount[car]
        for (s in 0 until SessionHistoryPacket.MAX_STINTS) {
            val st = h.stints[s]
            val k = car * MockCars.MAX_STINTS + s
            if (s < cars.stintCount[car]) {
                st.endLap = cars.stintEndLap[k]; st.visualCompound = cars.stintVisual[k]; st.actualCompound = cars.stintActual[k]
            } else {
                st.endLap = 0; st.visualCompound = 0; st.actualCompound = 0
            }
        }
        return h
    }

    fun buildTyreSets(): TyreSetsPacket {
        header(tyreSets.header, PacketId.TYRE_SETS)
        val p = race.playerIndex
        tyreSets.carIdx = p
        val fittedVisual = cars.visualCompound[p]
        var fittedIdx = -1
        val usedSoft = (cars.stintCount[p] - 1).coerceAtLeast(0)
        for (k in 0 until TyreSetsPacket.MAX_SETS) {
            val t = tyreSets.sets[k]
            val visual = SET_VISUAL[k]
            t.visualCompound = visual
            t.actualCompound = SET_ACTUAL[k]
            val isFitted = fittedIdx < 0 && visual == fittedVisual
            if (isFitted) fittedIdx = k
            val used = !isFitted && visual == MockRace.VISUAL_SOFT && k < usedSoft
            t.fitted = isFitted
            t.available = !used
            t.wearPercent = when {
                isFitted -> (cars.tyreWear[p] * 100).roundToInt()
                used -> 38
                else -> 0
            }
            t.recommendedSession = if (visual == MockRace.VISUAL_SOFT) 5 else 15
            t.usableLifeLaps = when (visual) { MockRace.VISUAL_SOFT -> 14; MockRace.VISUAL_MEDIUM -> 22; MockRace.VISUAL_HARD -> 32; else -> 28 }
            t.lifeSpanLaps = (t.usableLifeLaps - if (isFitted) cars.tyreAge[p] else if (used) 5 else 0).coerceAtLeast(0)
            t.lapDeltaTimeMs = SET_PACE_MS[k] - if (fittedIdx >= 0) SET_PACE_MS[fittedIdx] else SET_PACE_MS[0]
        }
        tyreSets.fittedIdx = fittedIdx.coerceAtLeast(0)
        return tyreSets
    }

    fun buildLapPositions(): LapPositionsPacket {
        header(lapPositions.header, PacketId.LAP_POSITIONS)
        var maxLap = 0
        for (i in 0 until race.carCount) if (cars.lap[i] > maxLap) maxLap = cars.lap[i]
        maxLap = maxLap.coerceAtMost(MockCars.MAX_LAPS)
        val start = (maxLap - LapPositionsPacket.MAX_LAPS).coerceAtLeast(0)
        lapPositions.lapStart = start
        lapPositions.numLaps = (maxLap - start).coerceAtLeast(0)
        for (lap in 0 until LapPositionsPacket.MAX_LAPS) for (car in 0 until format.maxCars) {
            val absolute = start + lap
            lapPositions.positions[lap * com.dashwroom.f1telemetry.core.protocol.PacketFormat.MAX_CARS + car] =
                if (car < race.carCount && lap < lapPositions.numLaps && absolute < cars.lap[car]) {
                    cars.lapStartPosition[car * MockCars.MAX_LAPS + absolute]
                } else 0
        }
        return lapPositions
    }

    fun buildFinalClassification(): FinalClassificationPacket {
        header(classification.header, PacketId.FINAL_CLASSIFICATION)
        classification.numClassified = race.carCount
        for (i in 0 until format.maxCars) {
            val r = classification.cars[i]
            if (i >= race.carCount) {
                r.position = 0; r.numLaps = 0; r.gridPosition = 0; r.points = 0; r.numPitStops = 0; r.resultStatus = 0
                r.resultReason = 0; r.bestLapTimeMs = 0; r.totalRaceTimeSeconds = 0.0; r.penaltiesTimeSeconds = 0
                r.numPenalties = 0; r.numTyreStints = 0
                r.tyreStintsActual.fill(0); r.tyreStintsVisual.fill(0); r.tyreStintsEndLaps.fill(0)
                continue
            }
            r.position = cars.position[i]
            r.numLaps = cars.historyLaps[i]
            r.gridPosition = cars.gridPosition[i]
            r.points = POINTS.getOrElse(cars.position[i] - 1) { 0 }
            r.numPitStops = cars.pitStops[i]
            r.resultStatus = cars.resultStatus[i]
            r.resultReason = if (cars.resultStatus[i] == MockRace.RESULT_RETIRED) 8 else 2
            r.bestLapTimeMs = cars.bestLapMs[i]
            var total = 0.0
            for (l in 0 until cars.historyLaps[i]) total += cars.historyLapMs[i * MockCars.MAX_LAPS + l] / 1000.0
            r.totalRaceTimeSeconds = total
            r.penaltiesTimeSeconds = cars.penaltiesSeconds[i]
            r.numPenalties = if (cars.penaltiesSeconds[i] > 0) 1 else 0
            r.numTyreStints = cars.stintCount[i]
            for (s in 0 until FinalClassificationPacket.MAX_STINTS) {
                val k = i * MockCars.MAX_STINTS + s
                val used = s < cars.stintCount[i]
                r.tyreStintsActual[s] = if (used) cars.stintActual[k] else 0
                r.tyreStintsVisual[s] = if (used) cars.stintVisual[k] else 0
                r.tyreStintsEndLaps[s] = if (used) (if (cars.stintEndLap[k] == 255) cars.historyLaps[i] else cars.stintEndLap[k]) else 0
            }
        }
        return classification
    }

    fun buildMotionEx(): MotionExPacket {
        header(motionEx.header, PacketId.MOTION_EX)
        val p = race.playerIndex
        val m = motionEx
        val v = cars.speed[p]
        val idx = track.index(cars.lapDistance[p])
        val k = track.signedCurvature[idx]
        val a = cars.accel[p]
        for (w in 0 until 4) {
            val front = w >= 2
            val left = w % 2 == 0
            m.suspensionPosition[w] = 12f + v * 0.05f + (if (left) k else -k) * 400f
            m.suspensionVelocity[w] = 0f
            m.suspensionAcceleration[w] = 0f
            m.wheelSpeed[w] = v
            m.wheelSlipRatio[w] = if (front) (-a / 400f).coerceIn(-0.2f, 0.05f) else (a / 300f).coerceIn(-0.05f, 0.15f)
            m.wheelSlipAngle[w] = (k * 3f).coerceIn(-0.12f, 0.12f)
            m.wheelLatForce[w] = v * v * k * 180f
            m.wheelLongForce[w] = a * 190f
            m.wheelVertForce[w] = 1_900f + v * v * 0.35f
            m.wheelCamber[w] = if (front) -0.05f else -0.03f
            m.wheelCamberGain[w] = 0f
        }
        m.heightOfCogAboveGround = 0.28f
        m.localVelocityX = 0f; m.localVelocityY = 0f; m.localVelocityZ = v
        m.angularVelocityX = 0f; m.angularVelocityY = v * k; m.angularVelocityZ = 0f
        m.angularAccelerationX = 0f; m.angularAccelerationY = 0f; m.angularAccelerationZ = 0f
        m.frontWheelsAngle = (k * 4f).coerceIn(-0.4f, 0.4f)
        m.frontAeroHeight = (0.045f - v * v * 0.0000030f).coerceAtLeast(0.012f)
        m.rearAeroHeight = (0.085f - v * v * 0.0000040f).coerceAtLeast(0.030f)
        m.frontRollAngle = k * 0.8f
        m.rearRollAngle = k * 0.6f
        m.chassisYaw = k * 0.5f
        m.chassisPitch = -a * 0.0006f
        return m
    }

    fun buildTelemetry2(): CarTelemetry2Packet {
        header(telemetry2.header, PacketId.CAR_TELEMETRY_2)
        for (i in 0 until format.maxCars) {
            val t = telemetry2.cars[i]
            val active = i < race.carCount
            val inZone = active && track.inDrsZone(cars.lapDistance[i])
            t.activeAeroMode = if (inZone && race.safetyCarStatus == 0) 1 else 0
            t.activeAeroAvailable = active && race.drsEnabled
            t.activeAeroActivationDistance = if (active) track.distanceToDrsZone(cars.lapDistance[i]).roundToInt().coerceIn(0, 65_535) else 0
            t.overtakeAvailable = active && cars.drsAllowed[i]
            t.overtakeActive = active && cars.drsOpen[i]
            t.overtakeActivationDistance = if (active && !cars.drsAllowed[i]) 0 else t.activeAeroActivationDistance
            t.regulations2026 = active
            t.drivingWrongWay = false
        }
        return telemetry2
    }

    private fun setName(p: ParticipantsPacket.Participant, name: String) {
        if (p.name == name) return
        val bytes = name.toByteArray(Charsets.UTF_8)
        val len = minOf(bytes.size, ParticipantsPacket.NAME_LENGTH - 1)
        System.arraycopy(bytes, 0, p.nameBytes, 0, len)
        p.nameLength = len
        p.name = name
    }

    private fun zeroMotion(m: MotionPacket.CarMotion) {
        m.worldPositionX = 0f; m.worldPositionY = 0f; m.worldPositionZ = 0f
        m.worldVelocityX = 0f; m.worldVelocityY = 0f; m.worldVelocityZ = 0f
        m.worldForwardDirX = 0f; m.worldForwardDirY = 0f; m.worldForwardDirZ = 0f
        m.worldRightDirX = 0f; m.worldRightDirY = 0f; m.worldRightDirZ = 0f
        m.gForceLateral = 0f; m.gForceLongitudinal = 0f; m.gForceVertical = 0f
        m.yaw = 0f; m.pitch = 0f; m.roll = 0f
    }

    private fun zeroLap(l: LapDataPacket.LapData) {
        l.lastLapTimeMs = 0; l.currentLapTimeMs = 0; l.sector1TimeMs = 0; l.sector2TimeMs = 0
        l.deltaToCarInFrontMs = 0; l.deltaToRaceLeaderMs = 0; l.lapDistance = 0f; l.totalDistance = 0f
        l.safetyCarDelta = 0f; l.carPosition = 0; l.currentLapNum = 0; l.pitStatus = 0; l.numPitStops = 0
        l.sector = 0; l.currentLapInvalid = false; l.penaltiesSeconds = 0; l.totalWarnings = 0
        l.cornerCuttingWarnings = 0; l.numUnservedDriveThroughPens = 0; l.numUnservedStopGoPens = 0
        l.gridPosition = 0; l.driverStatus = 0; l.resultStatus = 0; l.pitLaneTimerActive = false
        l.pitLaneTimeInLaneMs = 0; l.pitStopTimerMs = 0; l.pitStopShouldServePen = false
        l.speedTrapFastestSpeedKph = 0f; l.speedTrapFastestLap = 255
    }

    private fun zeroTelemetry(t: CarTelemetryPacket.CarTelemetry) {
        t.speedKph = 0; t.throttle = 0f; t.steer = 0f; t.brake = 0f; t.clutch = 0; t.gear = 0
        t.engineRpm = 0; t.drs = false; t.revLightsPercent = 0; t.revLightsBitValue = 0
        t.brakesTemperature.fill(0); t.tyresSurfaceTemperature.fill(0); t.tyresInnerTemperature.fill(0)
        t.engineTemperature = 0; t.tyresPressure.fill(0f); t.surfaceType.fill(0)
    }

    private fun zeroStatus(s: CarStatusPacket.CarStatus) {
        s.tractionControl = 0; s.antiLockBrakes = false; s.fuelMix = 0; s.frontBrakeBias = 0
        s.pitLimiterOn = false; s.fuelInTank = 0f; s.fuelCapacity = 0f; s.fuelRemainingLaps = 0f
        s.maxRpm = 0; s.idleRpm = 0; s.maxGears = 0; s.drsAllowed = false; s.drsActivationDistance = 0
        s.actualTyreCompound = 0; s.visualTyreCompound = 0; s.tyresAgeLaps = 0; s.vehicleFiaFlags = FLAG_INVALID
        s.enginePowerIce = 0f; s.enginePowerMguk = 0f; s.ersStoreEnergy = 0f; s.ersDeployMode = 0
        s.ersHarvestedThisLapMguk = 0f; s.ersHarvestedThisLapMguh = 0f
        s.ersHarvestLimitPerLap = if (format == PacketFormat.F1_25_SEASON_2026) 0f else Float.NaN
        s.ersDeployedThisLap = 0f; s.networkPaused = false
    }

    private fun gearFor(kph: Float): Int {
        var g = 1
        while (g < 8 && kph >= GEAR_UP_KPH[g]) g++
        return g
    }

    private fun rpmFor(kph: Float, gear: Int): Int {
        if (kph < 1f) return IDLE_RPM
        val lo = GEAR_UP_KPH[gear - 1]
        val hi = if (gear < 8) GEAR_UP_KPH[gear] else 345f
        val f = ((kph - lo) / (hi - lo)).coerceIn(0f, 1f)
        val floor = if (gear == 1) IDLE_RPM else 10_300
        return (floor + f * (MAX_RPM - 300 - floor)).roundToInt()
    }

    private companion object {
        const val G = 9.81f
        const val MAX_DELTA_MS = 59 * 60_000 + 59_999
        const val DRIVER_IN_LAP = 2
        const val DRIVER_ON_TRACK = 4
        const val MAX_RPM = 13_000
        const val IDLE_RPM = 4_000
        const val REV_LIGHTS_START = 10_500
        const val FLAG_INVALID = -1
        const val FLAG_NONE = 0
        const val FLAG_YELLOW = 3
        const val SESSION_RACE = 15
        const val TRACK_SILVERSTONE = 7

        /** Speed (km/h) at which each gear is reached; index 0 = gear 1. */
        val GEAR_UP_KPH = floatArrayOf(0f, 95f, 130f, 165f, 200f, 235f, 268f, 298f)
        val WEEKEND = intArrayOf(1, 2, 3, 13, SESSION_RACE)
        const val PIT_LANE_OFFSET_M = 18f

        /** RL, RR, FL, FR — a clockwise lap works the left-front hardest. */
        val WHEEL_WEAR_BIAS = floatArrayOf(0.90f, 0.86f, 1.08f, 0.97f)

        // Tyre allocation: 8 soft, 3 medium, 2 hard, 4 intermediate, 3 wet.
        val SET_VISUAL = intArrayOf(16, 16, 16, 16, 16, 16, 16, 16, 17, 17, 17, 18, 18, 7, 7, 7, 7, 8, 8, 8)
        val SET_ACTUAL = intArrayOf(18, 18, 18, 18, 18, 18, 18, 18, 19, 19, 19, 20, 20, 7, 7, 7, 7, 8, 8, 8)
        val SET_PACE_MS = intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 450, 450, 450, 950, 950, 4200, 4200, 4200, 4200, 7800, 7800, 7800)
        val POINTS = intArrayOf(25, 18, 15, 12, 10, 8, 6, 4, 2, 1)
    }
}
