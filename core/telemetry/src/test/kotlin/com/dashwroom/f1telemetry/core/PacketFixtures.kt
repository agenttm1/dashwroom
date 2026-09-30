package com.dashwroom.f1telemetry.core

import com.dashwroom.f1telemetry.core.encode.PacketEncoder
import com.dashwroom.f1telemetry.core.encode.StructWriter
import com.dashwroom.f1telemetry.core.packet.CarDamagePacket
import com.dashwroom.f1telemetry.core.packet.CarStatusPacket
import com.dashwroom.f1telemetry.core.packet.CarTelemetry2Packet
import com.dashwroom.f1telemetry.core.packet.FinalClassificationPacket
import com.dashwroom.f1telemetry.core.packet.LapPositionsPacket
import com.dashwroom.f1telemetry.core.packet.MotionExPacket
import com.dashwroom.f1telemetry.core.packet.SessionHistoryPacket
import com.dashwroom.f1telemetry.core.packet.TyreSetsPacket
import com.dashwroom.f1telemetry.core.packet.CarTelemetryPacket
import com.dashwroom.f1telemetry.core.packet.EventPacket
import com.dashwroom.f1telemetry.core.packet.LapDataPacket
import com.dashwroom.f1telemetry.core.packet.MotionPacket
import com.dashwroom.f1telemetry.core.packet.PacketHeader
import com.dashwroom.f1telemetry.core.packet.ParticipantsPacket
import com.dashwroom.f1telemetry.core.packet.SessionPacket
import com.dashwroom.f1telemetry.core.protocol.PacketFormat
import com.dashwroom.f1telemetry.core.protocol.PacketId
import com.dashwroom.f1telemetry.core.protocol.PacketSizes
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.random.Random

/** Randomised, in-range packet contents for round-trip tests. */
class PacketFixtures(seed: Int = 1234) {
    private val r = Random(seed)

    private fun u8() = r.nextInt(0, 256)
    private fun i8() = r.nextInt(-128, 128)
    private fun u16() = r.nextInt(0, 65_536)
    private fun u32() = r.nextLong(0, 0x1_0000_0000L)
    private fun f() = r.nextFloat() * 2000f - 1000f
    private fun unit() = r.nextFloat()
    private fun bool() = r.nextBoolean()

    /** Values that survive int16 quantisation exactly. */
    private fun dir() = r.nextInt(-32767, 32768) / 32767f
    private fun g() = r.nextInt(-32768, 32768) / 1000f

    fun header(h: PacketHeader, format: PacketFormat, packetId: Int) {
        h.format = format
        h.packetFormat = format.wireValue
        h.gameYear = 25
        h.gameMajorVersion = u8()
        h.gameMinorVersion = u8()
        h.packetVersion = 1
        h.packetId = packetId
        h.sessionUid = r.nextLong()
        h.sessionTime = unit() * 5000f
        h.frameIdentifier = u32()
        h.overallFrameIdentifier = u32()
        h.playerCarIndex = r.nextInt(0, format.maxCars)
        h.secondaryPlayerCarIndex = 255
    }

    fun motion(format: PacketFormat) = MotionPacket().apply {
        header(header, format, PacketId.MOTION)
        val quantised = format == PacketFormat.F1_25_SEASON_2026
        for (i in 0 until numCars) with(cars[i]) {
            worldPositionX = f(); worldPositionY = f(); worldPositionZ = f()
            worldVelocityX = f(); worldVelocityY = f(); worldVelocityZ = f()
            worldForwardDirX = dir(); worldForwardDirY = dir(); worldForwardDirZ = dir()
            worldRightDirX = dir(); worldRightDirY = dir(); worldRightDirZ = dir()
            gForceLateral = if (quantised) g() else f()
            gForceLongitudinal = if (quantised) g() else f()
            gForceVertical = if (quantised) g() else f()
            yaw = f(); pitch = f(); roll = f()
        }
    }

    fun lapData(format: PacketFormat) = LapDataPacket().apply {
        header(header, format, PacketId.LAP_DATA)
        for (i in 0 until numCars) with(cars[i]) {
            lastLapTimeMs = u32(); currentLapTimeMs = u32()
            sector1TimeMs = r.nextInt(0, 256 * 60_000); sector2TimeMs = r.nextInt(0, 256 * 60_000)
            deltaToCarInFrontMs = r.nextInt(0, 256 * 60_000); deltaToRaceLeaderMs = r.nextInt(0, 256 * 60_000)
            lapDistance = f(); totalDistance = f(); safetyCarDelta = f()
            carPosition = u8(); currentLapNum = u8(); pitStatus = u8(); numPitStops = u8(); sector = u8()
            currentLapInvalid = bool(); penaltiesSeconds = u8(); totalWarnings = u8(); cornerCuttingWarnings = u8()
            numUnservedDriveThroughPens = u8(); numUnservedStopGoPens = u8(); gridPosition = u8()
            driverStatus = u8(); resultStatus = u8(); pitLaneTimerActive = bool()
            pitLaneTimeInLaneMs = u16(); pitStopTimerMs = u16(); pitStopShouldServePen = bool()
            speedTrapFastestSpeedKph = f(); speedTrapFastestLap = u8()
        }
        timeTrialPbCarIdx = u8(); timeTrialRivalCarIdx = u8()
    }

    fun telemetry(format: PacketFormat) = CarTelemetryPacket().apply {
        header(header, format, PacketId.CAR_TELEMETRY)
        for (i in 0 until numCars) with(cars[i]) {
            speedKph = u16(); throttle = unit(); steer = unit() * 2 - 1; brake = unit(); clutch = u8()
            gear = i8(); engineRpm = u16(); drs = bool(); revLightsPercent = u8(); revLightsBitValue = u16()
            for (w in 0 until 4) {
                brakesTemperature[w] = u16(); tyresSurfaceTemperature[w] = u8(); tyresInnerTemperature[w] = u8()
                tyresPressure[w] = f(); surfaceType[w] = u8()
            }
            engineTemperature = if (format == PacketFormat.F1_25_SEASON_2026) u8() else u16()
        }
        mfdPanelIndex = u8(); mfdPanelIndexSecondaryPlayer = u8(); suggestedGear = i8()
    }

    fun status(format: PacketFormat) = CarStatusPacket().apply {
        header(header, format, PacketId.CAR_STATUS)
        for (i in 0 until numCars) with(cars[i]) {
            tractionControl = u8(); antiLockBrakes = bool(); fuelMix = u8(); frontBrakeBias = u8()
            pitLimiterOn = bool(); fuelInTank = f(); fuelCapacity = f(); fuelRemainingLaps = f()
            maxRpm = u16(); idleRpm = u16(); maxGears = u8(); drsAllowed = bool(); drsActivationDistance = u16()
            actualTyreCompound = u8(); visualTyreCompound = u8(); tyresAgeLaps = u8(); vehicleFiaFlags = i8()
            enginePowerIce = f(); enginePowerMguk = f(); ersStoreEnergy = f(); ersDeployMode = u8()
            ersHarvestedThisLapMguk = f(); ersHarvestedThisLapMguh = f()
            ersHarvestLimitPerLap = if (format == PacketFormat.F1_25_SEASON_2026) f() else Float.NaN
            ersDeployedThisLap = f(); networkPaused = bool()
        }
    }

    fun session(format: PacketFormat) = SessionPacket().apply {
        header(header, format, PacketId.SESSION)
        weather = u8(); trackTemperature = i8(); airTemperature = i8(); totalLaps = u8(); trackLength = u16()
        sessionType = u8(); trackId = i8(); formula = u8(); sessionTimeLeft = u16(); sessionDuration = u16()
        pitSpeedLimit = u8(); gamePaused = bool(); isSpectating = bool(); spectatorCarIndex = u8()
        sliProNativeSupport = bool(); numMarshalZones = r.nextInt(0, SessionPacket.MAX_MARSHAL_ZONES + 1)
        for (z in 0 until SessionPacket.MAX_MARSHAL_ZONES) { marshalZoneStart[z] = unit(); marshalZoneFlag[z] = i8() }
        safetyCarStatus = u8(); networkGame = bool()
        numWeatherForecastSamples = r.nextInt(0, SessionPacket.MAX_WEATHER_SAMPLES + 1)
        for (w in weatherForecast) {
            w.sessionType = u8(); w.timeOffsetMinutes = u8(); w.weather = u8(); w.trackTemperature = i8()
            w.trackTemperatureChange = i8(); w.airTemperature = i8(); w.airTemperatureChange = i8(); w.rainPercentage = u8()
        }
        forecastAccuracy = u8(); aiDifficulty = u8()
        seasonLinkIdentifier = u32(); weekendLinkIdentifier = u32(); sessionLinkIdentifier = u32()
        pitStopWindowIdealLap = u8(); pitStopWindowLatestLap = u8(); pitStopRejoinPosition = u8()
        steeringAssist = u8(); brakingAssist = u8(); gearboxAssist = u8(); pitAssist = u8(); pitReleaseAssist = u8()
        ersAssist = u8(); drsAssist = u8(); dynamicRacingLine = u8(); dynamicRacingLineType = u8(); gameMode = u8()
        ruleSet = u8(); timeOfDayMinutes = u32(); sessionLength = u8(); speedUnitsLeadPlayer = u8()
        temperatureUnitsLeadPlayer = u8(); speedUnitsSecondaryPlayer = u8(); temperatureUnitsSecondaryPlayer = u8()
        numSafetyCarPeriods = u8(); numVirtualSafetyCarPeriods = u8(); numRedFlagPeriods = u8()
        equalCarPerformance = u8(); recoveryMode = u8(); flashbackLimit = u8(); surfaceType = u8(); lowFuelMode = u8()
        raceStarts = u8(); tyreTemperature = u8(); pitLaneTyreSim = u8(); carDamage = u8(); carDamageRate = u8()
        collisions = u8(); collisionsOffForFirstLapOnly = u8(); mpUnsafePitRelease = u8(); mpOffForGriefing = u8()
        cornerCuttingStringency = u8(); parcFermeRules = u8(); pitStopExperience = u8(); safetyCar = u8()
        safetyCarExperience = u8(); formationLap = u8(); formationLapExperience = u8(); redFlags = u8()
        affectsLicenceLevelSolo = u8(); affectsLicenceLevelMp = u8()
        numSessionsInWeekend = r.nextInt(0, SessionPacket.MAX_SESSIONS_IN_WEEKEND + 1)
        for (s in 0 until SessionPacket.MAX_SESSIONS_IN_WEEKEND) weekendStructure[s] = u8()
        sector2LapDistanceStart = f(); sector3LapDistanceStart = f()
        if (format == PacketFormat.F1_25_SEASON_2026) {
            activeAeroTrackStatus = u8()
            numActiveAeroZonesFull = r.nextInt(0, SessionPacket.MAX_ACTIVE_AERO_ZONES + 1)
            numActiveAeroZonesPartial = r.nextInt(0, SessionPacket.MAX_ACTIVE_AERO_ZONES + 1)
            for (z in 0 until SessionPacket.MAX_ACTIVE_AERO_ZONES) {
                activeAeroZonesFullStart[z] = unit(); activeAeroZonesFullEnd[z] = unit()
                activeAeroZonesPartialStart[z] = unit(); activeAeroZonesPartialEnd[z] = unit()
            }
            numDrsZones = r.nextInt(0, SessionPacket.MAX_DRS_ZONES + 1)
            for (z in 0 until SessionPacket.MAX_DRS_ZONES) { drsZoneStart[z] = unit(); drsZoneEnd[z] = unit() }
            startReactionTime = unit(); antiLockBrakesAssist = u8(); tractionControlAssist = u8()
            dynamicRacingLineHiVis = u8(); dynamicRacingLineColourBlind = u8(); recurringRewindPrompt = u8()
        }
    }

    fun participants(format: PacketFormat) = ParticipantsPacket().apply {
        header(header, format, PacketId.PARTICIPANTS)
        val wide = format == PacketFormat.F1_25_SEASON_2026
        numActiveCars = r.nextInt(0, numCars + 1)
        for (i in 0 until numCars) with(cars[i]) {
            aiControlled = bool()
            driverId = if (wide) u16() else u8()
            networkId = if (wide) u16() else u8()
            teamId = if (wide) u16() else u8()
            myTeam = bool(); raceNumber = u8(); nationality = u8()
            val n = NAMES[i % NAMES.size]
            val bytes = n.toByteArray(Charsets.UTF_8)
            bytes.copyInto(nameBytes)
            nameLength = bytes.size
            name = n
            yourTelemetryPublic = bool(); showOnlineNames = bool(); techLevel = u16(); platform = u8(); numColours = u8()
            for (k in 0 until ParticipantsPacket.MAX_LIVERY_COLOURS) liveryColours[k] = r.nextInt(0, 0x100_0000)
        }
    }

    fun event(format: PacketFormat, code: Int) = EventPacket().apply {
        header(header, format, PacketId.EVENT)
        this.code = code
        vehicleIdx = u8(); reason = u8(); lapTimeSeconds = f(); stopTimeSeconds = f(); numLights = u8()
        with(penalty) {
            penaltyType = u8(); infringementType = u8(); vehicleIdx = u8(); otherVehicleIdx = u8()
            timeSeconds = u8(); lapNum = u8(); placesGained = u8()
        }
        with(speedTrap) {
            vehicleIdx = u8(); speedKph = f(); isOverallFastestInSession = bool(); isDriverFastestInSession = bool()
            fastestVehicleIdxInSession = u8(); fastestSpeedInSession = f()
        }
        flashbackFrameIdentifier = u32(); flashbackSessionTime = f(); buttonStatus = u32()
        overtakingVehicleIdx = u8(); beingOvertakenVehicleIdx = u8(); safetyCarType = u8(); safetyCarEventType = u8()
        collisionVehicle1Idx = u8(); collisionVehicle2Idx = u8(); collisionSeverity = u8()
    }

    fun finalClassification(format: PacketFormat) = FinalClassificationPacket().apply {
        header(header, format, PacketId.FINAL_CLASSIFICATION)
        numClassified = r.nextInt(0, numCars + 1)
        for (i in 0 until numCars) with(cars[i]) {
            position = u8(); numLaps = u8(); gridPosition = u8(); points = u8(); numPitStops = u8()
            resultStatus = u8(); resultReason = u8(); bestLapTimeMs = u32(); totalRaceTimeSeconds = r.nextDouble() * 9000
            penaltiesTimeSeconds = u8(); numPenalties = u8(); numTyreStints = r.nextInt(0, 9)
            for (k in 0 until 8) { tyreStintsActual[k] = u8(); tyreStintsVisual[k] = u8(); tyreStintsEndLaps[k] = u8() }
        }
    }

    fun damage(format: PacketFormat) = CarDamagePacket().apply {
        header(header, format, PacketId.CAR_DAMAGE)
        for (i in 0 until numCars) with(cars[i]) {
            for (w in 0 until 4) { tyresWear[w] = unit() * 100; tyresDamage[w] = u8(); brakesDamage[w] = u8(); tyreBlisters[w] = u8() }
            frontLeftWingDamage = u8(); frontRightWingDamage = u8(); rearWingDamage = u8(); floorDamage = u8()
            diffuserDamage = u8(); sidepodDamage = u8(); drsFault = bool(); ersFault = bool(); gearBoxDamage = u8()
            engineDamage = u8(); engineMguhWear = u8(); engineEsWear = u8(); engineCeWear = u8(); engineIceWear = u8()
            engineMgukWear = u8(); engineTcWear = u8(); engineBlown = bool(); engineSeized = bool()
        }
    }

    fun sessionHistory(format: PacketFormat) = SessionHistoryPacket().apply {
        header(header, format, PacketId.SESSION_HISTORY)
        carIdx = r.nextInt(0, format.maxCars); numLaps = r.nextInt(0, 101); numTyreStints = r.nextInt(0, 9)
        bestLapTimeLapNum = u8(); bestSector1LapNum = u8(); bestSector2LapNum = u8(); bestSector3LapNum = u8()
        for (lap in laps) {
            lap.lapTimeMs = u32(); lap.sector1Ms = r.nextInt(0, 256 * 60_000); lap.sector2Ms = r.nextInt(0, 256 * 60_000)
            lap.sector3Ms = r.nextInt(0, 256 * 60_000); lap.validFlags = u8()
        }
        for (st in stints) { st.endLap = u8(); st.actualCompound = u8(); st.visualCompound = u8() }
    }

    fun tyreSets(format: PacketFormat) = TyreSetsPacket().apply {
        header(header, format, PacketId.TYRE_SETS)
        carIdx = r.nextInt(0, format.maxCars)
        for (t in sets) {
            t.actualCompound = u8(); t.visualCompound = u8(); t.wearPercent = u8(); t.available = bool()
            t.recommendedSession = u8(); t.lifeSpanLaps = u8(); t.usableLifeLaps = u8()
            t.lapDeltaTimeMs = r.nextInt(-32768, 32768); t.fitted = bool()
        }
        fittedIdx = u8()
    }

    fun motionEx(format: PacketFormat) = MotionExPacket().apply {
        header(header, format, PacketId.MOTION_EX)
        for (w in 0 until 4) {
            suspensionPosition[w] = f(); suspensionVelocity[w] = f(); suspensionAcceleration[w] = f(); wheelSpeed[w] = f()
            wheelSlipRatio[w] = f(); wheelSlipAngle[w] = f(); wheelLatForce[w] = f(); wheelLongForce[w] = f()
            wheelVertForce[w] = f(); wheelCamber[w] = f(); wheelCamberGain[w] = f()
        }
        heightOfCogAboveGround = f(); localVelocityX = f(); localVelocityY = f(); localVelocityZ = f()
        angularVelocityX = f(); angularVelocityY = f(); angularVelocityZ = f(); angularAccelerationX = f()
        angularAccelerationY = f(); angularAccelerationZ = f(); frontWheelsAngle = f(); frontAeroHeight = f()
        rearAeroHeight = f(); frontRollAngle = f(); rearRollAngle = f(); chassisYaw = f(); chassisPitch = f()
    }

    fun lapPositions(format: PacketFormat) = LapPositionsPacket().apply {
        header(header, format, PacketId.LAP_POSITIONS)
        numLaps = r.nextInt(0, 51); lapStart = u8()
        for (lap in 0 until LapPositionsPacket.MAX_LAPS) for (car in 0 until numCars) {
            positions[lap * PacketFormat.MAX_CARS + car] = u8()
        }
    }

    fun telemetry2(format: PacketFormat) = CarTelemetry2Packet().apply {
        header(header, format, PacketId.CAR_TELEMETRY_2)
        for (i in 0 until numCars) with(cars[i]) {
            activeAeroMode = u8(); activeAeroAvailable = bool(); activeAeroActivationDistance = u16()
            overtakeAvailable = bool(); overtakeActive = bool(); overtakeActivationDistance = u16()
            regulations2026 = bool(); drivingWrongWay = bool()
        }
    }

    companion object {
        /** Includes multi-byte UTF-8 and a full 31-byte name to exercise the name field edges. */
        val NAMES = listOf("NORRIS", "Hülkenberg", "Pérez", "O'Sullivan", "ÉÈÊËÀÂ-ÆØÅ ok", "abcdefghijklmnopqrstuvwxyz01234")
    }
}

/** Encodes with [encode] into a fresh little-endian buffer sized exactly to the result. */
fun encodePacket(encode: (StructWriter) -> Unit): ByteBuffer {
    val w = StructWriter(PacketSizes.MAX_DATAGRAM)
    encode(w)
    val out = w.bytes.copyOf(w.position)
    return ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN)
}

/** A zero-filled datagram of the spec size with a valid header for [format]/[packetId]. */
fun blankPacket(format: PacketFormat, packetId: Int): ByteBuffer {
    val size = PacketSizes.expected(format, packetId)
    val buf = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
    buf.putShort(0, format.wireValue.toShort())
    buf.put(6, packetId.toByte())
    return buf
}

fun encodeAny(packet: Any): ByteBuffer = encodePacket { w ->
    when (packet) {
        is MotionPacket -> PacketEncoder.motion(w, packet)
        is SessionPacket -> PacketEncoder.session(w, packet)
        is LapDataPacket -> PacketEncoder.lapData(w, packet)
        is EventPacket -> PacketEncoder.event(w, packet)
        is ParticipantsPacket -> PacketEncoder.participants(w, packet)
        is CarTelemetryPacket -> PacketEncoder.carTelemetry(w, packet)
        is CarStatusPacket -> PacketEncoder.carStatus(w, packet)
        is FinalClassificationPacket -> PacketEncoder.finalClassification(w, packet)
        is CarDamagePacket -> PacketEncoder.carDamage(w, packet)
        is SessionHistoryPacket -> PacketEncoder.sessionHistory(w, packet)
        is TyreSetsPacket -> PacketEncoder.tyreSets(w, packet)
        is MotionExPacket -> PacketEncoder.motionEx(w, packet)
        is LapPositionsPacket -> PacketEncoder.lapPositions(w, packet)
        is CarTelemetry2Packet -> PacketEncoder.carTelemetry2(w, packet)
        else -> error("unsupported $packet")
    }
}
