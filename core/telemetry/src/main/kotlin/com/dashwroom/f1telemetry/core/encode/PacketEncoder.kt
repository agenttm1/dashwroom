package com.dashwroom.f1telemetry.core.encode

import com.dashwroom.f1telemetry.core.packet.CarDamagePacket
import com.dashwroom.f1telemetry.core.packet.CarStatusPacket
import com.dashwroom.f1telemetry.core.packet.CarTelemetry2Packet
import com.dashwroom.f1telemetry.core.packet.FinalClassificationPacket
import com.dashwroom.f1telemetry.core.packet.LapPositionsPacket
import com.dashwroom.f1telemetry.core.packet.MotionExPacket
import com.dashwroom.f1telemetry.core.packet.SessionHistoryPacket
import com.dashwroom.f1telemetry.core.packet.TyreSetsPacket
import com.dashwroom.f1telemetry.core.packet.CarTelemetryPacket
import com.dashwroom.f1telemetry.core.packet.EventCode
import com.dashwroom.f1telemetry.core.packet.EventPacket
import com.dashwroom.f1telemetry.core.packet.LapDataPacket
import com.dashwroom.f1telemetry.core.packet.MotionPacket
import com.dashwroom.f1telemetry.core.packet.PacketHeader
import com.dashwroom.f1telemetry.core.packet.ParticipantsPacket
import com.dashwroom.f1telemetry.core.packet.SessionPacket
import com.dashwroom.f1telemetry.core.protocol.PacketFormat
import kotlin.math.roundToInt

/**
 * Serialises packet models back to spec-exact bytes (used by the mock emitter and tests).
 * Each function writes its struct fields in declaration order; the result length always equals
 * [com.dashwroom.f1telemetry.core.protocol.PacketSizes.expected]. Allocation-free.
 */
object PacketEncoder {

    fun header(w: StructWriter, h: PacketHeader) {
        w.u16(h.format.wireValue)
        w.u8(h.gameYear)
        w.u8(h.gameMajorVersion)
        w.u8(h.gameMinorVersion)
        w.u8(h.packetVersion)
        w.u8(h.packetId)
        w.u64(h.sessionUid)
        w.f32(h.sessionTime)
        w.u32(h.frameIdentifier)
        w.u32(h.overallFrameIdentifier)
        w.u8(h.playerCarIndex)
        w.u8(h.secondaryPlayerCarIndex)
    }

    fun motion(w: StructWriter, p: MotionPacket) {
        header(w, p.header)
        val quantised = p.format == PacketFormat.F1_25_SEASON_2026
        for (i in 0 until p.numCars) {
            val c = p.cars[i]
            w.f32(c.worldPositionX); w.f32(c.worldPositionY); w.f32(c.worldPositionZ)
            w.f32(c.worldVelocityX); w.f32(c.worldVelocityY); w.f32(c.worldVelocityZ)
            w.i16(dir(c.worldForwardDirX)); w.i16(dir(c.worldForwardDirY)); w.i16(dir(c.worldForwardDirZ))
            w.i16(dir(c.worldRightDirX)); w.i16(dir(c.worldRightDirY)); w.i16(dir(c.worldRightDirZ))
            if (quantised) {
                w.i16(g(c.gForceLateral)); w.i16(g(c.gForceLongitudinal)); w.i16(g(c.gForceVertical))
            } else {
                w.f32(c.gForceLateral); w.f32(c.gForceLongitudinal); w.f32(c.gForceVertical)
            }
            w.f32(c.yaw); w.f32(c.pitch); w.f32(c.roll)
        }
    }

    fun session(w: StructWriter, p: SessionPacket) {
        header(w, p.header)
        w.u8(p.weather); w.i8(p.trackTemperature); w.i8(p.airTemperature); w.u8(p.totalLaps)
        w.u16(p.trackLength); w.u8(p.sessionType); w.i8(p.trackId); w.u8(p.formula)
        w.u16(p.sessionTimeLeft); w.u16(p.sessionDuration); w.u8(p.pitSpeedLimit)
        w.bool(p.gamePaused); w.bool(p.isSpectating); w.u8(p.spectatorCarIndex); w.bool(p.sliProNativeSupport)
        w.u8(p.numMarshalZones)
        for (z in 0 until SessionPacket.MAX_MARSHAL_ZONES) {
            w.f32(p.marshalZoneStart[z]); w.i8(p.marshalZoneFlag[z])
        }
        w.u8(p.safetyCarStatus); w.bool(p.networkGame); w.u8(p.numWeatherForecastSamples)
        for (s in 0 until SessionPacket.MAX_WEATHER_SAMPLES) {
            val f = p.weatherForecast[s]
            w.u8(f.sessionType); w.u8(f.timeOffsetMinutes); w.u8(f.weather)
            w.i8(f.trackTemperature); w.i8(f.trackTemperatureChange)
            w.i8(f.airTemperature); w.i8(f.airTemperatureChange); w.u8(f.rainPercentage)
        }
        w.u8(p.forecastAccuracy); w.u8(p.aiDifficulty)
        w.u32(p.seasonLinkIdentifier); w.u32(p.weekendLinkIdentifier); w.u32(p.sessionLinkIdentifier)
        w.u8(p.pitStopWindowIdealLap); w.u8(p.pitStopWindowLatestLap); w.u8(p.pitStopRejoinPosition)
        w.u8(p.steeringAssist); w.u8(p.brakingAssist); w.u8(p.gearboxAssist); w.u8(p.pitAssist)
        w.u8(p.pitReleaseAssist); w.u8(p.ersAssist); w.u8(p.drsAssist); w.u8(p.dynamicRacingLine)
        w.u8(p.dynamicRacingLineType); w.u8(p.gameMode); w.u8(p.ruleSet)
        w.u32(p.timeOfDayMinutes); w.u8(p.sessionLength)
        w.u8(p.speedUnitsLeadPlayer); w.u8(p.temperatureUnitsLeadPlayer)
        w.u8(p.speedUnitsSecondaryPlayer); w.u8(p.temperatureUnitsSecondaryPlayer)
        w.u8(p.numSafetyCarPeriods); w.u8(p.numVirtualSafetyCarPeriods); w.u8(p.numRedFlagPeriods)
        w.u8(p.equalCarPerformance); w.u8(p.recoveryMode); w.u8(p.flashbackLimit); w.u8(p.surfaceType)
        w.u8(p.lowFuelMode); w.u8(p.raceStarts); w.u8(p.tyreTemperature); w.u8(p.pitLaneTyreSim)
        w.u8(p.carDamage); w.u8(p.carDamageRate); w.u8(p.collisions); w.u8(p.collisionsOffForFirstLapOnly)
        w.u8(p.mpUnsafePitRelease); w.u8(p.mpOffForGriefing); w.u8(p.cornerCuttingStringency)
        w.u8(p.parcFermeRules); w.u8(p.pitStopExperience); w.u8(p.safetyCar); w.u8(p.safetyCarExperience)
        w.u8(p.formationLap); w.u8(p.formationLapExperience); w.u8(p.redFlags)
        w.u8(p.affectsLicenceLevelSolo); w.u8(p.affectsLicenceLevelMp)
        w.u8(p.numSessionsInWeekend)
        for (s in 0 until SessionPacket.MAX_SESSIONS_IN_WEEKEND) w.u8(p.weekendStructure[s])
        w.f32(p.sector2LapDistanceStart); w.f32(p.sector3LapDistanceStart)
        if (p.format == PacketFormat.F1_25_SEASON_2026) {
            w.u8(p.activeAeroTrackStatus)
            w.u8(p.numActiveAeroZonesFull)
            for (z in 0 until SessionPacket.MAX_ACTIVE_AERO_ZONES) {
                w.f32(p.activeAeroZonesFullStart[z]); w.f32(p.activeAeroZonesFullEnd[z])
            }
            w.u8(p.numActiveAeroZonesPartial)
            for (z in 0 until SessionPacket.MAX_ACTIVE_AERO_ZONES) {
                w.f32(p.activeAeroZonesPartialStart[z]); w.f32(p.activeAeroZonesPartialEnd[z])
            }
            w.u8(p.numDrsZones)
            for (z in 0 until SessionPacket.MAX_DRS_ZONES) {
                w.f32(p.drsZoneStart[z]); w.f32(p.drsZoneEnd[z])
            }
            w.f32(p.startReactionTime)
            w.u8(p.antiLockBrakesAssist); w.u8(p.tractionControlAssist); w.u8(p.dynamicRacingLineHiVis)
            w.u8(p.dynamicRacingLineColourBlind); w.u8(p.recurringRewindPrompt)
        }
    }

    fun lapData(w: StructWriter, p: LapDataPacket) {
        header(w, p.header)
        for (i in 0 until p.numCars) {
            val c = p.cars[i]
            w.u32(c.lastLapTimeMs); w.u32(c.currentLapTimeMs)
            w.u16(c.sector1TimeMs % 60_000); w.u8(c.sector1TimeMs / 60_000)
            w.u16(c.sector2TimeMs % 60_000); w.u8(c.sector2TimeMs / 60_000)
            w.u16(c.deltaToCarInFrontMs % 60_000); w.u8(c.deltaToCarInFrontMs / 60_000)
            w.u16(c.deltaToRaceLeaderMs % 60_000); w.u8(c.deltaToRaceLeaderMs / 60_000)
            w.f32(c.lapDistance); w.f32(c.totalDistance); w.f32(c.safetyCarDelta)
            w.u8(c.carPosition); w.u8(c.currentLapNum); w.u8(c.pitStatus); w.u8(c.numPitStops)
            w.u8(c.sector); w.bool(c.currentLapInvalid); w.u8(c.penaltiesSeconds); w.u8(c.totalWarnings)
            w.u8(c.cornerCuttingWarnings); w.u8(c.numUnservedDriveThroughPens); w.u8(c.numUnservedStopGoPens)
            w.u8(c.gridPosition); w.u8(c.driverStatus); w.u8(c.resultStatus); w.bool(c.pitLaneTimerActive)
            w.u16(c.pitLaneTimeInLaneMs); w.u16(c.pitStopTimerMs); w.bool(c.pitStopShouldServePen)
            w.f32(c.speedTrapFastestSpeedKph); w.u8(c.speedTrapFastestLap)
        }
        w.u8(p.timeTrialPbCarIdx); w.u8(p.timeTrialRivalCarIdx)
    }

    fun event(w: StructWriter, p: EventPacket) {
        header(w, p.header)
        val code = p.code
        w.u8(code ushr 24); w.u8(code ushr 16); w.u8(code ushr 8); w.u8(code)
        val start = w.position
        when (code) {
            EventCode.FASTEST_LAP -> { w.u8(p.vehicleIdx); w.f32(p.lapTimeSeconds) }
            EventCode.RETIREMENT -> { w.u8(p.vehicleIdx); w.u8(p.reason) }
            EventCode.DRS_DISABLED, EventCode.PARTIAL_MODE_ENABLED -> w.u8(p.reason)
            EventCode.TEAM_MATE_IN_PITS, EventCode.RACE_WINNER, EventCode.DRIVE_THROUGH_SERVED -> w.u8(p.vehicleIdx)
            EventCode.PENALTY -> with(p.penalty) {
                w.u8(penaltyType); w.u8(infringementType); w.u8(vehicleIdx); w.u8(otherVehicleIdx)
                w.u8(timeSeconds); w.u8(lapNum); w.u8(placesGained)
            }
            EventCode.SPEED_TRAP -> with(p.speedTrap) {
                w.u8(vehicleIdx); w.f32(speedKph); w.bool(isOverallFastestInSession)
                w.bool(isDriverFastestInSession); w.u8(fastestVehicleIdxInSession); w.f32(fastestSpeedInSession)
            }
            EventCode.START_LIGHTS -> w.u8(p.numLights)
            EventCode.STOP_GO_SERVED -> { w.u8(p.vehicleIdx); w.f32(p.stopTimeSeconds) }
            EventCode.FLASHBACK -> { w.u32(p.flashbackFrameIdentifier); w.f32(p.flashbackSessionTime) }
            EventCode.BUTTON_STATUS -> w.u32(p.buttonStatus)
            EventCode.OVERTAKE -> { w.u8(p.overtakingVehicleIdx); w.u8(p.beingOvertakenVehicleIdx) }
            EventCode.SAFETY_CAR -> { w.u8(p.safetyCarType); w.u8(p.safetyCarEventType) }
            EventCode.COLLISION -> {
                w.u8(p.collisionVehicle1Idx); w.u8(p.collisionVehicle2Idx)
                if (p.format == PacketFormat.F1_25_SEASON_2026) w.u8(p.collisionSeverity)
            }
            else -> Unit
        }
        w.zeros(EVENT_UNION_SIZE - (w.position - start)) // pad the union to its full 12 bytes
    }

    fun participants(w: StructWriter, p: ParticipantsPacket) {
        header(w, p.header)
        val wide = p.format == PacketFormat.F1_25_SEASON_2026
        w.u8(p.numActiveCars)
        for (i in 0 until p.numCars) {
            val c = p.cars[i]
            w.bool(c.aiControlled)
            if (wide) {
                w.u16(c.driverId); w.u16(c.networkId); w.u16(c.teamId)
            } else {
                w.u8(c.driverId); w.u8(c.networkId); w.u8(c.teamId)
            }
            w.bool(c.myTeam); w.u8(c.raceNumber); w.u8(c.nationality)
            w.fixedBytes(c.nameBytes, c.nameLength, ParticipantsPacket.NAME_LENGTH)
            w.bool(c.yourTelemetryPublic); w.bool(c.showOnlineNames); w.u16(c.techLevel)
            w.u8(c.platform); w.u8(c.numColours)
            for (k in 0 until ParticipantsPacket.MAX_LIVERY_COLOURS) {
                val rgb = c.liveryColours[k]
                w.u8(rgb ushr 16); w.u8(rgb ushr 8); w.u8(rgb)
            }
        }
    }

    fun carTelemetry(w: StructWriter, p: CarTelemetryPacket) {
        header(w, p.header)
        val narrowEngineTemp = p.format == PacketFormat.F1_25_SEASON_2026
        for (i in 0 until p.numCars) {
            val c = p.cars[i]
            w.u16(c.speedKph); w.f32(c.throttle); w.f32(c.steer); w.f32(c.brake)
            w.u8(c.clutch); w.i8(c.gear); w.u16(c.engineRpm); w.bool(c.drs)
            w.u8(c.revLightsPercent); w.u16(c.revLightsBitValue)
            for (k in 0 until 4) w.u16(c.brakesTemperature[k])
            for (k in 0 until 4) w.u8(c.tyresSurfaceTemperature[k])
            for (k in 0 until 4) w.u8(c.tyresInnerTemperature[k])
            if (narrowEngineTemp) w.u8(c.engineTemperature) else w.u16(c.engineTemperature)
            for (k in 0 until 4) w.f32(c.tyresPressure[k])
            for (k in 0 until 4) w.u8(c.surfaceType[k])
        }
        w.u8(p.mfdPanelIndex); w.u8(p.mfdPanelIndexSecondaryPlayer); w.i8(p.suggestedGear)
    }

    fun carStatus(w: StructWriter, p: CarStatusPacket) {
        header(w, p.header)
        val is2026 = p.format == PacketFormat.F1_25_SEASON_2026
        for (i in 0 until p.numCars) {
            val c = p.cars[i]
            w.u8(c.tractionControl); w.bool(c.antiLockBrakes); w.u8(c.fuelMix); w.u8(c.frontBrakeBias)
            w.bool(c.pitLimiterOn); w.f32(c.fuelInTank); w.f32(c.fuelCapacity); w.f32(c.fuelRemainingLaps)
            w.u16(c.maxRpm); w.u16(c.idleRpm); w.u8(c.maxGears); w.bool(c.drsAllowed)
            w.u16(c.drsActivationDistance); w.u8(c.actualTyreCompound); w.u8(c.visualTyreCompound)
            w.u8(c.tyresAgeLaps); w.i8(c.vehicleFiaFlags); w.f32(c.enginePowerIce); w.f32(c.enginePowerMguk)
            w.f32(c.ersStoreEnergy); w.u8(c.ersDeployMode)
            w.f32(c.ersHarvestedThisLapMguk); w.f32(c.ersHarvestedThisLapMguh)
            if (is2026) w.f32(c.ersHarvestLimitPerLap)
            w.f32(c.ersDeployedThisLap); w.bool(c.networkPaused)
        }
    }

    fun finalClassification(w: StructWriter, p: FinalClassificationPacket) {
        header(w, p.header)
        w.u8(p.numClassified)
        for (i in 0 until p.numCars) {
            val r = p.cars[i]
            w.u8(r.position); w.u8(r.numLaps); w.u8(r.gridPosition); w.u8(r.points); w.u8(r.numPitStops)
            w.u8(r.resultStatus); w.u8(r.resultReason); w.u32(r.bestLapTimeMs); w.f64(r.totalRaceTimeSeconds)
            w.u8(r.penaltiesTimeSeconds); w.u8(r.numPenalties); w.u8(r.numTyreStints)
            for (s in 0 until FinalClassificationPacket.MAX_STINTS) w.u8(r.tyreStintsActual[s])
            for (s in 0 until FinalClassificationPacket.MAX_STINTS) w.u8(r.tyreStintsVisual[s])
            for (s in 0 until FinalClassificationPacket.MAX_STINTS) w.u8(r.tyreStintsEndLaps[s])
        }
    }

    fun carDamage(w: StructWriter, p: CarDamagePacket) {
        header(w, p.header)
        for (i in 0 until p.numCars) {
            val c = p.cars[i]
            for (k in 0 until 4) w.f32(c.tyresWear[k])
            for (k in 0 until 4) w.u8(c.tyresDamage[k])
            for (k in 0 until 4) w.u8(c.brakesDamage[k])
            for (k in 0 until 4) w.u8(c.tyreBlisters[k])
            w.u8(c.frontLeftWingDamage); w.u8(c.frontRightWingDamage); w.u8(c.rearWingDamage)
            w.u8(c.floorDamage); w.u8(c.diffuserDamage); w.u8(c.sidepodDamage)
            w.bool(c.drsFault); w.bool(c.ersFault); w.u8(c.gearBoxDamage); w.u8(c.engineDamage)
            w.u8(c.engineMguhWear); w.u8(c.engineEsWear); w.u8(c.engineCeWear); w.u8(c.engineIceWear)
            w.u8(c.engineMgukWear); w.u8(c.engineTcWear); w.bool(c.engineBlown); w.bool(c.engineSeized)
        }
    }

    fun sessionHistory(w: StructWriter, p: SessionHistoryPacket) {
        header(w, p.header)
        w.u8(p.carIdx); w.u8(p.numLaps); w.u8(p.numTyreStints)
        w.u8(p.bestLapTimeLapNum); w.u8(p.bestSector1LapNum); w.u8(p.bestSector2LapNum); w.u8(p.bestSector3LapNum)
        for (l in 0 until SessionHistoryPacket.MAX_LAPS) {
            val lap = p.laps[l]
            w.u32(lap.lapTimeMs)
            w.u16(lap.sector1Ms % 60_000); w.u8(lap.sector1Ms / 60_000)
            w.u16(lap.sector2Ms % 60_000); w.u8(lap.sector2Ms / 60_000)
            w.u16(lap.sector3Ms % 60_000); w.u8(lap.sector3Ms / 60_000)
            w.u8(lap.validFlags)
        }
        for (s in 0 until SessionHistoryPacket.MAX_STINTS) {
            val st = p.stints[s]
            w.u8(st.endLap); w.u8(st.actualCompound); w.u8(st.visualCompound)
        }
    }

    fun tyreSets(w: StructWriter, p: TyreSetsPacket) {
        header(w, p.header)
        w.u8(p.carIdx)
        for (s in 0 until TyreSetsPacket.MAX_SETS) {
            val t = p.sets[s]
            w.u8(t.actualCompound); w.u8(t.visualCompound); w.u8(t.wearPercent); w.bool(t.available)
            w.u8(t.recommendedSession); w.u8(t.lifeSpanLaps); w.u8(t.usableLifeLaps); w.i16(t.lapDeltaTimeMs)
            w.bool(t.fitted)
        }
        w.u8(p.fittedIdx)
    }

    fun motionEx(w: StructWriter, p: MotionExPacket) {
        header(w, p.header)
        for (k in 0 until 4) w.f32(p.suspensionPosition[k])
        for (k in 0 until 4) w.f32(p.suspensionVelocity[k])
        for (k in 0 until 4) w.f32(p.suspensionAcceleration[k])
        for (k in 0 until 4) w.f32(p.wheelSpeed[k])
        for (k in 0 until 4) w.f32(p.wheelSlipRatio[k])
        for (k in 0 until 4) w.f32(p.wheelSlipAngle[k])
        for (k in 0 until 4) w.f32(p.wheelLatForce[k])
        for (k in 0 until 4) w.f32(p.wheelLongForce[k])
        w.f32(p.heightOfCogAboveGround)
        w.f32(p.localVelocityX); w.f32(p.localVelocityY); w.f32(p.localVelocityZ)
        w.f32(p.angularVelocityX); w.f32(p.angularVelocityY); w.f32(p.angularVelocityZ)
        w.f32(p.angularAccelerationX); w.f32(p.angularAccelerationY); w.f32(p.angularAccelerationZ)
        w.f32(p.frontWheelsAngle)
        for (k in 0 until 4) w.f32(p.wheelVertForce[k])
        w.f32(p.frontAeroHeight); w.f32(p.rearAeroHeight); w.f32(p.frontRollAngle); w.f32(p.rearRollAngle)
        w.f32(p.chassisYaw); w.f32(p.chassisPitch)
        for (k in 0 until 4) w.f32(p.wheelCamber[k])
        for (k in 0 until 4) w.f32(p.wheelCamberGain[k])
    }

    fun lapPositions(w: StructWriter, p: LapPositionsPacket) {
        header(w, p.header)
        w.u8(p.numLaps); w.u8(p.lapStart)
        for (lap in 0 until LapPositionsPacket.MAX_LAPS) {
            for (car in 0 until p.numCars) w.u8(p.position(lap, car))
        }
    }

    fun carTelemetry2(w: StructWriter, p: CarTelemetry2Packet) {
        header(w, p.header)
        for (i in 0 until p.numCars) {
            val c = p.cars[i]
            w.u8(c.activeAeroMode); w.bool(c.activeAeroAvailable); w.u16(c.activeAeroActivationDistance)
            w.bool(c.overtakeAvailable); w.bool(c.overtakeActive); w.u16(c.overtakeActivationDistance)
            w.bool(c.regulations2026); w.bool(c.drivingWrongWay)
        }
    }

    private const val EVENT_UNION_SIZE = 12

    private fun dir(v: Float): Int = (v * 32767f).roundToInt().coerceIn(-32767, 32767)

    private fun g(v: Float): Int = (v * 1000f).roundToInt().coerceIn(-32768, 32767)
}
