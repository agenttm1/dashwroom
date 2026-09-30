package com.dashwroom.f1telemetry.core.parser

import com.dashwroom.f1telemetry.core.packet.LapDataPacket
import com.dashwroom.f1telemetry.core.protocol.f32
import com.dashwroom.f1telemetry.core.protocol.flag
import com.dashwroom.f1telemetry.core.protocol.u16
import com.dashwroom.f1telemetry.core.protocol.u32
import com.dashwroom.f1telemetry.core.protocol.u8
import java.nio.ByteBuffer

/**
 * Packet 2 — `PacketLapData` = header(29) + `LapData[N]` + 2 bytes.
 * `LapData` is 57 bytes in both formats.
 * 2025: 29 + 22×57 (1254) + 2 = 1285 bytes.   2026: 29 + 24×57 (1368) + 2 = 1399 bytes.
 *
 * `LapData`, offsets relative to the car's first byte:
 * ```
 * off  type    field
 *   0  uint32  m_lastLapTimeInMS
 *   4  uint32  m_currentLapTimeInMS
 *   8  uint16  m_sector1TimeMSPart
 *  10  uint8   m_sector1TimeMinutesPart
 *  11  uint16  m_sector2TimeMSPart
 *  13  uint8   m_sector2TimeMinutesPart
 *  14  uint16  m_deltaToCarInFrontMSPart
 *  16  uint8   m_deltaToCarInFrontMinutesPart
 *  17  uint16  m_deltaToRaceLeaderMSPart
 *  19  uint8   m_deltaToRaceLeaderMinutesPart
 *  20  float   m_lapDistance (m, may be negative before the line)
 *  24  float   m_totalDistance
 *  28  float   m_safetyCarDelta (s)
 *  32  uint8   m_carPosition
 *  33  uint8   m_currentLapNum
 *  34  uint8   m_pitStatus
 *  35  uint8   m_numPitStops
 *  36  uint8   m_sector
 *  37  uint8   m_currentLapInvalid
 *  38  uint8   m_penalties (s)
 *  39  uint8   m_totalWarnings
 *  40  uint8   m_cornerCuttingWarnings
 *  41  uint8   m_numUnservedDriveThroughPens
 *  42  uint8   m_numUnservedStopGoPens
 *  43  uint8   m_gridPosition
 *  44  uint8   m_driverStatus
 *  45  uint8   m_resultStatus
 *  46  uint8   m_pitLaneTimerActive
 *  47  uint16  m_pitLaneTimeInLaneInMS
 *  49  uint16  m_pitStopTimerInMS
 *  51  uint8   m_pitStopShouldServePen
 *  52  float   m_speedTrapFastestSpeed (km/h)
 *  56  uint8   m_speedTrapFastestLap (255 = not set)
 * ```
 * After the array: uint8 m_timeTrialPBCarIdx @ 29+N×57, uint8 m_timeTrialRivalCarIdx @ +1.
 */
internal object LapDataParser {
    private const val FIRST_CAR = 29
    const val STRIDE = 57

    fun parse(buf: ByteBuffer, out: LapDataPacket) {
        val n = out.numCars
        for (i in 0 until n) {
            val b = FIRST_CAR + i * STRIDE
            val car = out.cars[i]
            car.lastLapTimeMs = buf.u32(b + 0)
            car.currentLapTimeMs = buf.u32(b + 4)
            car.sector1TimeMs = buf.u8(b + 10) * 60_000 + buf.u16(b + 8)
            car.sector2TimeMs = buf.u8(b + 13) * 60_000 + buf.u16(b + 11)
            car.deltaToCarInFrontMs = buf.u8(b + 16) * 60_000 + buf.u16(b + 14)
            car.deltaToRaceLeaderMs = buf.u8(b + 19) * 60_000 + buf.u16(b + 17)
            car.lapDistance = buf.f32(b + 20)
            car.totalDistance = buf.f32(b + 24)
            car.safetyCarDelta = buf.f32(b + 28)
            car.carPosition = buf.u8(b + 32)
            car.currentLapNum = buf.u8(b + 33)
            car.pitStatus = buf.u8(b + 34)
            car.numPitStops = buf.u8(b + 35)
            car.sector = buf.u8(b + 36)
            car.currentLapInvalid = buf.flag(b + 37)
            car.penaltiesSeconds = buf.u8(b + 38)
            car.totalWarnings = buf.u8(b + 39)
            car.cornerCuttingWarnings = buf.u8(b + 40)
            car.numUnservedDriveThroughPens = buf.u8(b + 41)
            car.numUnservedStopGoPens = buf.u8(b + 42)
            car.gridPosition = buf.u8(b + 43)
            car.driverStatus = buf.u8(b + 44)
            car.resultStatus = buf.u8(b + 45)
            car.pitLaneTimerActive = buf.flag(b + 46)
            car.pitLaneTimeInLaneMs = buf.u16(b + 47)
            car.pitStopTimerMs = buf.u16(b + 49)
            car.pitStopShouldServePen = buf.flag(b + 51)
            car.speedTrapFastestSpeedKph = buf.f32(b + 52)
            car.speedTrapFastestLap = buf.u8(b + 56)
        }
        val tail = FIRST_CAR + n * STRIDE
        out.timeTrialPbCarIdx = buf.u8(tail)
        out.timeTrialRivalCarIdx = buf.u8(tail + 1)
    }
}
