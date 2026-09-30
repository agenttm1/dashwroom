package com.dashwroom.f1telemetry.core.parser

import com.dashwroom.f1telemetry.core.packet.TyreSetsPacket
import com.dashwroom.f1telemetry.core.protocol.flag
import com.dashwroom.f1telemetry.core.protocol.i16
import com.dashwroom.f1telemetry.core.protocol.u8
import java.nio.ByteBuffer

/**
 * Packet 12 — `PacketTyreSetsData`, 231 bytes in both formats.
 * ```
 * 29  uint8 m_carIdx
 * 30  TyreSetData[20], 10 bytes each (30..229):
 *       0 uint8 m_actualTyreCompound   1 uint8 m_visualTyreCompound   2 uint8 m_wear (%)
 *       3 uint8 m_available            4 uint8 m_recommendedSession   5 uint8 m_lifeSpan (laps)
 *       6 uint8 m_usableLife (laps)    7 int16 m_lapDeltaTime (ms)    9 uint8 m_fitted
 * 230 uint8 m_fittedIdx
 * ```
 */
internal object TyreSetsParser {
    private const val SETS = 30
    private const val STRIDE = 10

    fun parse(buf: ByteBuffer, out: TyreSetsPacket) {
        out.carIdx = buf.u8(29)
        for (s in 0 until TyreSetsPacket.MAX_SETS) {
            val b = SETS + s * STRIDE
            val t = out.sets[s]
            t.actualCompound = buf.u8(b)
            t.visualCompound = buf.u8(b + 1)
            t.wearPercent = buf.u8(b + 2)
            t.available = buf.flag(b + 3)
            t.recommendedSession = buf.u8(b + 4)
            t.lifeSpanLaps = buf.u8(b + 5)
            t.usableLifeLaps = buf.u8(b + 6)
            t.lapDeltaTimeMs = buf.i16(b + 7)
            t.fitted = buf.flag(b + 9)
        }
        out.fittedIdx = buf.u8(230)
    }
}
