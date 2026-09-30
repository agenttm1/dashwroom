package com.dashwroom.f1telemetry.core.parser

import com.dashwroom.f1telemetry.core.packet.FinalClassificationPacket
import com.dashwroom.f1telemetry.core.protocol.f64
import com.dashwroom.f1telemetry.core.protocol.u32
import com.dashwroom.f1telemetry.core.protocol.u8
import java.nio.ByteBuffer

/**
 * Packet 8 — `PacketFinalClassificationData` = header(29) + uint8 m_numCars @29 + data @30, 46 bytes/car.
 * 2025: 30 + 22×46 = 1042 bytes.   2026: 30 + 24×46 = 1134 bytes.
 * ```
 *  0 uint8 m_position       1 uint8 m_numLaps       2 uint8 m_gridPosition   3 uint8 m_points
 *  4 uint8 m_numPitStops    5 uint8 m_resultStatus  6 uint8 m_resultReason
 *  7 uint32 m_bestLapTimeInMS                      11 double m_totalRaceTime (s, no penalties)
 * 19 uint8 m_penaltiesTime 20 uint8 m_numPenalties 21 uint8 m_numTyreStints
 * 22 uint8[8] m_tyreStintsActual   30 uint8[8] m_tyreStintsVisual   38 uint8[8] m_tyreStintsEndLaps
 * ```
 */
internal object FinalClassificationParser {
    private const val FIRST_CAR = 30
    private const val STRIDE = 46

    fun parse(buf: ByteBuffer, out: FinalClassificationPacket) {
        out.numClassified = buf.u8(29).coerceAtMost(out.numCars)
        for (i in 0 until out.numCars) {
            val b = FIRST_CAR + i * STRIDE
            val r = out.cars[i]
            r.position = buf.u8(b)
            r.numLaps = buf.u8(b + 1)
            r.gridPosition = buf.u8(b + 2)
            r.points = buf.u8(b + 3)
            r.numPitStops = buf.u8(b + 4)
            r.resultStatus = buf.u8(b + 5)
            r.resultReason = buf.u8(b + 6)
            r.bestLapTimeMs = buf.u32(b + 7)
            r.totalRaceTimeSeconds = buf.f64(b + 11)
            r.penaltiesTimeSeconds = buf.u8(b + 19)
            r.numPenalties = buf.u8(b + 20)
            r.numTyreStints = buf.u8(b + 21).coerceAtMost(FinalClassificationPacket.MAX_STINTS)
            for (s in 0 until FinalClassificationPacket.MAX_STINTS) {
                r.tyreStintsActual[s] = buf.u8(b + 22 + s)
                r.tyreStintsVisual[s] = buf.u8(b + 30 + s)
                r.tyreStintsEndLaps[s] = buf.u8(b + 38 + s)
            }
        }
    }
}
