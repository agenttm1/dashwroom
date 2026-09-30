package com.dashwroom.f1telemetry.core.parser

import com.dashwroom.f1telemetry.core.packet.ParticipantsPacket
import com.dashwroom.f1telemetry.core.protocol.PacketFormat
import com.dashwroom.f1telemetry.core.protocol.flag
import com.dashwroom.f1telemetry.core.protocol.u16
import com.dashwroom.f1telemetry.core.protocol.u8
import java.nio.ByteBuffer

/**
 * Packet 4 — `PacketParticipantsData` = header(29) + uint8 m_numActiveCars @29 + `ParticipantData[N]` @30.
 * 2025: stride 57 → 30 + 22×57 (1254) = 1284 bytes.
 * 2026: stride 60 → 30 + 24×60 (1440) = 1470 bytes (driver/network/team ids widened to uint16).
 *
 * `ParticipantData`, offsets relative to the car's first byte:
 * ```
 * field                         2025          2026
 * m_aiControlled                uint8@0       uint8@0
 * m_driverId                    uint8@1       uint16@1   (255 / 65535 = network human)
 * m_networkId                   uint8@2       uint16@3
 * m_teamId                      uint8@3       uint16@5
 * m_myTeam                      uint8@4       uint8@7
 * m_raceNumber                  uint8@5       uint8@8
 * m_nationality                 uint8@6       uint8@9
 * m_name char[32] (UTF-8, NUL)  @7..38        @10..41
 * m_yourTelemetry               uint8@39      uint8@42   (0 = restricted, 1 = public)
 * m_showOnlineNames             uint8@40      uint8@43
 * m_techLevel                   uint16@41     uint16@44
 * m_platform                    uint8@43      uint8@46
 * m_numColours                  uint8@44      uint8@47
 * m_liveryColours[4] (RGB ×3)   @45..56       @48..59
 *                       stride  57            60
 * ```
 */
internal object ParticipantsParser {
    private const val NUM_ACTIVE_CARS = 29
    private const val FIRST_CAR = 30

    fun parse(buf: ByteBuffer, out: ParticipantsPacket) {
        val wide = out.format == PacketFormat.F1_25_SEASON_2026
        val stride = if (wide) 60 else 57
        out.numActiveCars = buf.u8(NUM_ACTIVE_CARS)
        for (i in 0 until out.numCars) {
            val b = FIRST_CAR + i * stride
            val p = out.cars[i]
            p.aiControlled = buf.flag(b)
            val o: Int // offset of m_myTeam; everything after it shifts by 3 in 2026
            if (wide) {
                p.driverId = buf.u16(b + 1)
                p.networkId = buf.u16(b + 3)
                p.teamId = buf.u16(b + 5)
                o = b + 7
            } else {
                p.driverId = buf.u8(b + 1)
                p.networkId = buf.u8(b + 2)
                p.teamId = buf.u8(b + 3)
                o = b + 4
            }
            p.myTeam = buf.flag(o + 0)
            p.raceNumber = buf.u8(o + 1)
            p.nationality = buf.u8(o + 2)
            readName(buf, o + 3, p)
            p.yourTelemetryPublic = buf.flag(o + 35)
            p.showOnlineNames = buf.flag(o + 36)
            p.techLevel = buf.u16(o + 37)
            p.platform = buf.u8(o + 39)
            p.numColours = buf.u8(o + 40)
            for (c in 0 until ParticipantsPacket.MAX_LIVERY_COLOURS) {
                val cb = o + 41 + c * 3
                p.liveryColours[c] = (buf.u8(cb) shl 16) or (buf.u8(cb + 1) shl 8) or buf.u8(cb + 2)
            }
        }
    }

    /** Copies the NUL-terminated name and only decodes a new String when the bytes changed. */
    private fun readName(buf: ByteBuffer, offset: Int, p: ParticipantsPacket.Participant) {
        var len = 0
        var changed = false
        while (len < ParticipantsPacket.NAME_LENGTH) {
            val byte = buf.get(offset + len)
            if (byte.toInt() == 0) break
            if (p.nameBytes[len] != byte) changed = true
            len++
        }
        if (!changed && len == p.nameLength) return
        for (k in 0 until len) p.nameBytes[k] = buf.get(offset + k)
        p.nameLength = len
        p.name = String(p.nameBytes, 0, len, Charsets.UTF_8)
    }
}
