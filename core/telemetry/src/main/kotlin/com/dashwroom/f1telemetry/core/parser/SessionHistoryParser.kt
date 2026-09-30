package com.dashwroom.f1telemetry.core.parser

import com.dashwroom.f1telemetry.core.packet.SessionHistoryPacket
import com.dashwroom.f1telemetry.core.protocol.u16
import com.dashwroom.f1telemetry.core.protocol.u32
import com.dashwroom.f1telemetry.core.protocol.u8
import java.nio.ByteBuffer

/**
 * Packet 11 — `PacketSessionHistoryData`, 1460 bytes in both formats.
 * ```
 * 29  uint8 m_carIdx            30  uint8 m_numLaps           31  uint8 m_numTyreStints
 * 32  uint8 m_bestLapTimeLapNum 33  uint8 m_bestSector1LapNum 34  uint8 m_bestSector2LapNum
 * 35  uint8 m_bestSector3LapNum
 * 36  LapHistoryData[100], 14 bytes each (36..1435):
 *        0 uint32 m_lapTimeInMS
 *        4 uint16 m_sector1TimeMSPart    6 uint8 m_sector1TimeMinutesPart
 *        7 uint16 m_sector2TimeMSPart    9 uint8 m_sector2TimeMinutesPart
 *       10 uint16 m_sector3TimeMSPart   12 uint8 m_sector3TimeMinutesPart
 *       13 uint8  m_lapValidBitFlags
 * 1436 TyreStintHistoryData[8], 3 bytes each (1436..1459):
 *        0 uint8 m_endLap (255 = current)  1 uint8 m_tyreActualCompound  2 uint8 m_tyreVisualCompound
 * ```
 */
internal object SessionHistoryParser {
    private const val LAPS = 36
    private const val LAP_STRIDE = 14
    private const val STINTS = 1436
    private const val STINT_STRIDE = 3

    fun parse(buf: ByteBuffer, out: SessionHistoryPacket) {
        out.carIdx = buf.u8(29)
        out.numLaps = buf.u8(30).coerceAtMost(SessionHistoryPacket.MAX_LAPS)
        out.numTyreStints = buf.u8(31).coerceAtMost(SessionHistoryPacket.MAX_STINTS)
        out.bestLapTimeLapNum = buf.u8(32)
        out.bestSector1LapNum = buf.u8(33)
        out.bestSector2LapNum = buf.u8(34)
        out.bestSector3LapNum = buf.u8(35)
        for (l in 0 until SessionHistoryPacket.MAX_LAPS) {
            val b = LAPS + l * LAP_STRIDE
            val lap = out.laps[l]
            lap.lapTimeMs = buf.u32(b)
            lap.sector1Ms = buf.u8(b + 6) * 60_000 + buf.u16(b + 4)
            lap.sector2Ms = buf.u8(b + 9) * 60_000 + buf.u16(b + 7)
            lap.sector3Ms = buf.u8(b + 12) * 60_000 + buf.u16(b + 10)
            lap.validFlags = buf.u8(b + 13)
        }
        for (s in 0 until SessionHistoryPacket.MAX_STINTS) {
            val b = STINTS + s * STINT_STRIDE
            val stint = out.stints[s]
            stint.endLap = buf.u8(b)
            stint.actualCompound = buf.u8(b + 1)
            stint.visualCompound = buf.u8(b + 2)
        }
    }
}
