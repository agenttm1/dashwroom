package com.dashwroom.f1telemetry.core.parser

import com.dashwroom.f1telemetry.core.blankPacket
import com.dashwroom.f1telemetry.core.packet.CarStatusPacket
import com.dashwroom.f1telemetry.core.packet.CarTelemetryPacket
import com.dashwroom.f1telemetry.core.packet.LapDataPacket
import com.dashwroom.f1telemetry.core.packet.MotionPacket
import com.dashwroom.f1telemetry.core.packet.ParticipantsPacket
import com.dashwroom.f1telemetry.core.packet.SessionPacket
import com.dashwroom.f1telemetry.core.protocol.PacketFormat.F1_25
import com.dashwroom.f1telemetry.core.protocol.PacketFormat.F1_25_SEASON_2026
import com.dashwroom.f1telemetry.core.protocol.PacketId
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.nio.ByteBuffer

/**
 * Values poked into zeroed datagrams at absolute offsets computed by hand from the spec structs
 * (header 29 bytes + index × struct size + field offset). Independent of the encoder.
 */
class GoldenOffsetTest {
    private val parser = PacketParser()

    private fun parse(buf: ByteBuffer) = parser.parse(buf, buf.capacity()).also {
        assertThat(parser.lastResult).isEqualTo(ParseResult.OK)
    }

    @Test
    fun `car telemetry 2025 - 60 byte stride, uint16 engine temp, pressures at +40`() {
        val b = blankPacket(F1_25, PacketId.CAR_TELEMETRY)
        val car = 29 + 7 * 60 // vehicle 7
        b.putShort(car + 0, 312) // m_speed
        b.putFloat(car + 2, 0.75f) // m_throttle
        b.put(car + 15, (-1).toByte()) // m_gear = R
        b.putShort(car + 16, 11_850) // m_engineRPM
        b.put(car + 18, 1) // m_drs
        b.putShort(car + 22 + 3 * 2, 987) // m_brakesTemperature[FR]
        b.put(car + 30 + 2, 101) // m_tyresSurfaceTemperature[FL]
        b.putShort(car + 38, 300) // m_engineTemperature (uint16 in 2025)
        b.putFloat(car + 40 + 4, 22.5f) // m_tyresPressure[RR]
        b.put(car + 56 + 3, 7) // m_surfaceType[FR] = grass
        b.put(29 + 22 * 60 + 2, (-1).toByte()) // m_suggestedGear after the array (1349 + 2)
        val p = parse(b) as CarTelemetryPacket
        with(p.cars[7]) {
            assertThat(speedKph).isEqualTo(312)
            assertThat(throttle).isEqualTo(0.75f)
            assertThat(gear).isEqualTo(-1)
            assertThat(engineRpm).isEqualTo(11_850)
            assertThat(drs).isTrue()
            assertThat(brakesTemperature[3]).isEqualTo(987)
            assertThat(tyresSurfaceTemperature[2]).isEqualTo(101)
            assertThat(engineTemperature).isEqualTo(300)
            assertThat(tyresPressure[1]).isEqualTo(22.5f)
            assertThat(surfaceType[3]).isEqualTo(7)
        }
        assertThat(p.suggestedGear).isEqualTo(-1)
    }

    @Test
    fun `car telemetry 2026 - 59 byte stride, uint8 engine temp, pressures at +39`() {
        val b = blankPacket(F1_25_SEASON_2026, PacketId.CAR_TELEMETRY)
        val car = 29 + 23 * 59 // vehicle 23 exists only in 2026
        b.putShort(car + 0, 201)
        b.put(car + 38, 115.toByte()) // m_engineTemperature (uint8)
        b.putFloat(car + 39, 24.25f) // m_tyresPressure[RL]
        b.put(car + 55, 4) // m_surfaceType[RL] = gravel
        b.put(29 + 24 * 59, 3) // m_mfdPanelIndex @1445
        val p = parse(b) as CarTelemetryPacket
        assertThat(p.cars[23].speedKph).isEqualTo(201)
        assertThat(p.cars[23].engineTemperature).isEqualTo(115)
        assertThat(p.cars[23].tyresPressure[0]).isEqualTo(24.25f)
        assertThat(p.cars[23].surfaceType[0]).isEqualTo(4)
        assertThat(p.mfdPanelIndex).isEqualTo(3)
    }

    @Test
    fun `car status - 2026 inserts harvest limit before deployed-this-lap`() {
        val b25 = blankPacket(F1_25, PacketId.CAR_STATUS)
        val c25 = 29 + 3 * 55
        b25.putFloat(c25 + 5, 42.5f) // m_fuelInTank
        b25.putShort(c25 + 23, 180) // m_drsActivationDistance
        b25.put(c25 + 26, 17) // m_visualTyreCompound = medium
        b25.put(c25 + 28, (-1).toByte()) // m_vehicleFIAFlags
        b25.putFloat(c25 + 37, 3.9e6f) // m_ersStoreEnergy
        b25.putFloat(c25 + 50, 1.25e6f) // m_ersDeployedThisLap (2025)
        b25.put(c25 + 54, 1) // m_networkPaused (2025)
        with((parse(b25) as CarStatusPacket).cars[3]) {
            assertThat(fuelInTank).isEqualTo(42.5f)
            assertThat(drsActivationDistance).isEqualTo(180)
            assertThat(visualTyreCompound).isEqualTo(17)
            assertThat(vehicleFiaFlags).isEqualTo(-1)
            assertThat(ersStoreEnergy).isEqualTo(3.9e6f)
            assertThat(ersDeployedThisLap).isEqualTo(1.25e6f)
            assertThat(ersHarvestLimitPerLap).isNaN()
            assertThat(networkPaused).isTrue()
        }

        val b26 = blankPacket(F1_25_SEASON_2026, PacketId.CAR_STATUS)
        val c26 = 29 + 3 * 59
        b26.putFloat(c26 + 50, 8.5e6f) // m_ersHarvestLimitPerLap
        b26.putFloat(c26 + 54, 2.0e6f) // m_ersDeployedThisLap
        b26.put(c26 + 58, 1) // m_networkPaused
        with((parse(b26) as CarStatusPacket).cars[3]) {
            assertThat(ersHarvestLimitPerLap).isEqualTo(8.5e6f)
            assertThat(ersDeployedThisLap).isEqualTo(2.0e6f)
            assertThat(networkPaused).isTrue()
        }
    }

    @Test
    fun `lap data - minute and millisecond parts combine, tail bytes after the array`() {
        val b = blankPacket(F1_25, PacketId.LAP_DATA)
        val car = 29 + 2 * 57
        b.putInt(car + 0, 91_234) // m_lastLapTimeInMS
        b.putShort(car + 8, 12_345) // m_sector1TimeMSPart
        b.put(car + 10, 1) // m_sector1TimeMinutesPart
        b.putShort(car + 17, 500) // m_deltaToRaceLeaderMSPart
        b.put(car + 19, 2) // m_deltaToRaceLeaderMinutesPart
        b.putFloat(car + 20, -12.5f) // m_lapDistance before the line
        b.put(car + 32, 4) // m_carPosition
        b.put(car + 45, 7) // m_resultStatus = retired
        b.putShort(car + 49, 2_450) // m_pitStopTimerInMS
        b.put(car + 56, 255.toByte()) // m_speedTrapFastestLap
        b.put(29 + 22 * 57, 9) // m_timeTrialPBCarIdx @1283
        b.put(29 + 22 * 57 + 1, 255.toByte()) // m_timeTrialRivalCarIdx @1284
        val p = parse(b) as LapDataPacket
        with(p.cars[2]) {
            assertThat(lastLapTimeMs).isEqualTo(91_234)
            assertThat(sector1TimeMs).isEqualTo(72_345)
            assertThat(deltaToRaceLeaderMs).isEqualTo(120_500)
            assertThat(lapDistance).isEqualTo(-12.5f)
            assertThat(carPosition).isEqualTo(4)
            assertThat(resultStatus).isEqualTo(7)
            assertThat(pitStopTimerMs).isEqualTo(2_450)
            assertThat(speedTrapFastestLap).isEqualTo(255)
        }
        assertThat(p.timeTrialPbCarIdx).isEqualTo(9)
        assertThat(p.timeTrialRivalCarIdx).isEqualTo(255)
    }

    @Test
    fun `participants - uint8 ids in 2025, uint16 ids and shifted name in 2026`() {
        val b25 = blankPacket(F1_25, PacketId.PARTICIPANTS)
        b25.put(29, 20) // m_numActiveCars
        val c25 = 30 + 1 * 57
        b25.put(c25 + 1, 54) // m_driverId
        b25.put(c25 + 3, 8) // m_teamId
        b25.put(c25 + 5, 4) // m_raceNumber
        "NORRIS".toByteArray().forEachIndexed { k, v -> b25.put(c25 + 7 + k, v) } // m_name @+7
        b25.put(c25 + 39, 1) // m_yourTelemetry
        b25.put(c25 + 45, 0xFF.toByte()); b25.put(c25 + 46, 0x80.toByte()); b25.put(c25 + 47, 0x00) // livery[0]
        val p25 = parse(b25) as ParticipantsPacket
        assertThat(p25.numActiveCars).isEqualTo(20)
        with(p25.cars[1]) {
            assertThat(driverId).isEqualTo(54)
            assertThat(teamId).isEqualTo(8)
            assertThat(raceNumber).isEqualTo(4)
            assertThat(name).isEqualTo("NORRIS")
            assertThat(yourTelemetryPublic).isTrue()
            assertThat(liveryColours[0]).isEqualTo(0xFF8000)
        }

        val b26 = blankPacket(F1_25_SEASON_2026, PacketId.PARTICIPANTS)
        val c26 = 30 + 22 * 60 // vehicle 22 exists only in 2026
        b26.putShort(c26 + 1, 65_535.toShort()) // m_driverId = network human
        b26.putShort(c26 + 5, 486) // m_teamId = Cadillac '26
        b26.put(c26 + 8, 11) // m_raceNumber
        "Pérez".toByteArray().forEachIndexed { k, v -> b26.put(c26 + 10 + k, v) } // m_name @+10
        b26.putShort(c26 + 44, 1234) // m_techLevel
        b26.put(c26 + 46, 3) // m_platform = PlayStation
        with((parse(b26) as ParticipantsPacket).cars[22]) {
            assertThat(driverId).isEqualTo(65_535)
            assertThat(teamId).isEqualTo(486)
            assertThat(raceNumber).isEqualTo(11)
            assertThat(name).isEqualTo("Pérez")
            assertThat(techLevel).isEqualTo(1234)
            assertThat(platform).isEqualTo(3)
        }
    }

    @Test
    fun `session - fixed prefix shared by both formats and the 2026-only tail`() {
        val b = blankPacket(F1_25_SEASON_2026, PacketId.SESSION)
        b.put(32, 57) // m_totalLaps
        b.putShort(33, 5891) // m_trackLength
        b.put(35, 15) // m_sessionType = race
        b.put(36, 7) // m_trackId = Silverstone
        b.put(48 + 5 * 3 + 4, 3) // marshal zone 3 flag = yellow
        b.put(153, 2) // m_safetyCarStatus = VSC
        b.put(156 + 8 * 2 + 7, 60) // weather sample 2 rain %
        b.putInt(696, 900) // m_timeOfDay
        b.put(733 + 4, 15) // m_weekendStructure[4]
        b.putFloat(749, 3900.5f) // m_sector3LapDistanceStart
        b.put(884, 2) // m_numDRSZones
        b.putFloat(885 + 8 + 4, 0.61f) // DRS zone 1 end
        b.putFloat(917, 0.187f) // m_startReactionTime
        b.put(925, 1) // m_recurringRewindPrompt (last byte, 925)
        val p = parse(b) as SessionPacket
        assertThat(p.totalLaps).isEqualTo(57)
        assertThat(p.trackLength).isEqualTo(5891)
        assertThat(p.sessionType).isEqualTo(15)
        assertThat(p.trackId).isEqualTo(7)
        assertThat(p.marshalZoneFlag[3]).isEqualTo(3)
        assertThat(p.safetyCarStatus).isEqualTo(2)
        assertThat(p.weatherForecast[2].rainPercentage).isEqualTo(60)
        assertThat(p.timeOfDayMinutes).isEqualTo(900)
        assertThat(p.weekendStructure[4]).isEqualTo(15)
        assertThat(p.sector3LapDistanceStart).isEqualTo(3900.5f)
        assertThat(p.numDrsZones).isEqualTo(2)
        assertThat(p.drsZoneEnd[1]).isEqualTo(0.61f)
        assertThat(p.startReactionTime).isEqualTo(0.187f)
        assertThat(p.recurringRewindPrompt).isEqualTo(1)
    }

    @Test
    fun `motion - 2026 quantised g-force and shifted angles`() {
        val b25 = blankPacket(F1_25, PacketId.MOTION)
        val c25 = 29 + 21 * 60
        b25.putFloat(c25 + 0, 1234.5f) // m_worldPositionX
        b25.putShort(c25 + 24, 32767) // m_worldForwardDirX = 1.0
        b25.putFloat(c25 + 36, -3.25f) // m_gForceLateral (float)
        b25.putFloat(c25 + 48, 1.5f) // m_yaw
        with((parse(b25) as MotionPacket).cars[21]) {
            assertThat(worldPositionX).isEqualTo(1234.5f)
            assertThat(worldForwardDirX).isEqualTo(1f)
            assertThat(gForceLateral).isEqualTo(-3.25f)
            assertThat(yaw).isEqualTo(1.5f)
        }

        val b26 = blankPacket(F1_25_SEASON_2026, PacketId.MOTION)
        val c26 = 29 + 21 * 54
        b26.putShort(c26 + 36, (-3250).toShort()) // m_gForceLateral (int16 ÷ 1000)
        b26.putShort(c26 + 40, 1000) // m_gForceVertical
        b26.putFloat(c26 + 42, 1.5f) // m_yaw
        b26.putFloat(c26 + 50, -0.25f) // m_roll
        with((parse(b26) as MotionPacket).cars[21]) {
            assertThat(gForceLateral).isWithin(1e-6f).of(-3.25f)
            assertThat(gForceVertical).isWithin(1e-6f).of(1f)
            assertThat(yaw).isEqualTo(1.5f)
            assertThat(roll).isEqualTo(-0.25f)
        }
    }
}

class GoldenOffsetPhase3Test {
    private val parser = PacketParser()

    private fun parse(buf: ByteBuffer) = parser.parse(buf, buf.capacity()).also {
        assertThat(parser.lastResult).isEqualTo(ParseResult.OK)
    }

    @Test
    fun `car damage - 46 byte stride, engine wear block`() {
        val b = blankPacket(F1_25_SEASON_2026, PacketId.CAR_DAMAGE)
        val car = 29 + 23 * 46
        b.putFloat(car + 8, 37.5f) // m_tyresWear[FL]
        b.put(car + 28, 12) // m_frontLeftWingDamage
        b.put(car + 31, 44) // m_floorDamage
        b.put(car + 41, 23) // m_engineICEWear
        b.put(car + 45, 1) // m_engineSeized
        val c = (parse(b) as com.dashwroom.f1telemetry.core.packet.CarDamagePacket).cars[23]
        assertThat(c.tyresWear[2]).isEqualTo(37.5f)
        assertThat(c.frontLeftWingDamage).isEqualTo(12)
        assertThat(c.floorDamage).isEqualTo(44)
        assertThat(c.engineIceWear).isEqualTo(23)
        assertThat(c.engineSeized).isTrue()
    }

    @Test
    fun `session history - 14 byte laps from 36, stints from 1436`() {
        val b = blankPacket(F1_25, PacketId.SESSION_HISTORY)
        b.put(29, 7); b.put(30, 12); b.put(31, 2); b.put(32, 9) // carIdx, numLaps, stints, best lap
        val lap9 = 36 + 8 * 14
        b.putInt(lap9, 89_456) // m_lapTimeInMS
        b.putShort(lap9 + 7, 31_250); b.put(lap9 + 9, 0) // sector 2 = 31.250 s
        b.put(lap9 + 13, 0x0F) // all valid
        b.put(1436 + 3, 20); b.put(1436 + 5, 18) // stint 1: ends lap 20, visual hard
        val p = parse(b) as com.dashwroom.f1telemetry.core.packet.SessionHistoryPacket
        assertThat(p.carIdx).isEqualTo(7)
        assertThat(p.bestLapTimeLapNum).isEqualTo(9)
        assertThat(p.laps[8].lapTimeMs).isEqualTo(89_456)
        assertThat(p.laps[8].sector2Ms).isEqualTo(31_250)
        assertThat(p.laps[8].validFlags).isEqualTo(0x0F)
        assertThat(p.stints[1].endLap).isEqualTo(20)
        assertThat(p.stints[1].visualCompound).isEqualTo(18)
    }

    @Test
    fun `tyre sets - int16 lap delta at +7, fitted index at 230`() {
        val b = blankPacket(F1_25, PacketId.TYRE_SETS)
        val set4 = 30 + 4 * 10
        b.put(set4 + 1, 16) // visual soft
        b.put(set4 + 2, 35) // wear
        b.putShort(set4 + 7, (-420).toShort()) // lap delta
        b.put(set4 + 9, 1) // fitted
        b.put(230, 4)
        val p = parse(b) as com.dashwroom.f1telemetry.core.packet.TyreSetsPacket
        assertThat(p.sets[4].visualCompound).isEqualTo(16)
        assertThat(p.sets[4].wearPercent).isEqualTo(35)
        assertThat(p.sets[4].lapDeltaTimeMs).isEqualTo(-420)
        assertThat(p.sets[4].fitted).isTrue()
        assertThat(p.fittedIdx).isEqualTo(4)
    }

    @Test
    fun `lap positions - row per lap, N cars wide`() {
        val b = blankPacket(F1_25_SEASON_2026, PacketId.LAP_POSITIONS)
        b.put(29, 3); b.put(30, 10)
        b.put(31 + 2 * 24 + 21, 5) // lap row 2, vehicle 21 → P5
        val p = parse(b) as com.dashwroom.f1telemetry.core.packet.LapPositionsPacket
        assertThat(p.lapStart).isEqualTo(10)
        assertThat(p.position(2, 21)).isEqualTo(5)
    }

    @Test
    fun `final classification - double race time at +11`() {
        val b = blankPacket(F1_25, PacketId.FINAL_CLASSIFICATION)
        b.put(29, 20)
        val car = 30 + 3 * 46
        b.put(car, 1); b.putInt(car + 7, 88_123); b.putDouble(car + 11, 5_432.125); b.put(car + 21, 2)
        b.put(car + 30 + 1, 18); b.put(car + 38 + 1, 255.toByte())
        val p = parse(b) as com.dashwroom.f1telemetry.core.packet.FinalClassificationPacket
        assertThat(p.numClassified).isEqualTo(20)
        with(p.cars[3]) {
            assertThat(position).isEqualTo(1)
            assertThat(bestLapTimeMs).isEqualTo(88_123)
            assertThat(totalRaceTimeSeconds).isEqualTo(5_432.125)
            assertThat(tyreStintsVisual[1]).isEqualTo(18)
            assertThat(tyreStintsEndLaps[1]).isEqualTo(255)
        }
    }

    @Test
    fun `motion ex - aero heights and camber gain tail`() {
        val b = blankPacket(F1_25, PacketId.MOTION_EX)
        b.putFloat(217, 0.031f); b.putFloat(221, 0.064f); b.putFloat(257 + 12, -0.004f)
        val p = parse(b) as com.dashwroom.f1telemetry.core.packet.MotionExPacket
        assertThat(p.frontAeroHeight).isEqualTo(0.031f)
        assertThat(p.rearAeroHeight).isEqualTo(0.064f)
        assertThat(p.wheelCamberGain[3]).isEqualTo(-0.004f)
    }

    @Test
    fun `car telemetry 2 - overtake fields`() {
        val b = blankPacket(F1_25_SEASON_2026, PacketId.CAR_TELEMETRY_2)
        val car = 29 + 5 * 10
        b.put(car, 1); b.put(car + 5, 1); b.putShort(car + 6, 340)
        with((parse(b) as com.dashwroom.f1telemetry.core.packet.CarTelemetry2Packet).cars[5]) {
            assertThat(activeAeroMode).isEqualTo(1)
            assertThat(overtakeActive).isTrue()
            assertThat(overtakeActivationDistance).isEqualTo(340)
        }
    }
}
