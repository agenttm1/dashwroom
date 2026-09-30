package com.dashwroom.f1telemetry.core.parser

import com.dashwroom.f1telemetry.core.packet.PacketHeader
import com.dashwroom.f1telemetry.core.protocol.u16
import com.dashwroom.f1telemetry.core.protocol.u32
import com.dashwroom.f1telemetry.core.protocol.u64
import com.dashwroom.f1telemetry.core.protocol.u8
import java.nio.ByteBuffer

/**
 * `struct PacketHeader` — 29 bytes, packed, little-endian. Same layout in 2025 and 2026.
 *
 * ```
 * off size type    field
 *   0   2  uint16  m_packetFormat            2025 / 2026
 *   2   1  uint8   m_gameYear                last two digits, e.g. 25
 *   3   1  uint8   m_gameMajorVersion        "X.00"
 *   4   1  uint8   m_gameMinorVersion        "1.XX"
 *   5   1  uint8   m_packetVersion
 *   6   1  uint8   m_packetId
 *   7   8  uint64  m_sessionUID
 *  15   4  float   m_sessionTime
 *  19   4  uint32  m_frameIdentifier
 *  23   4  uint32  m_overallFrameIdentifier  never goes back after flashbacks
 *  27   1  uint8   m_playerCarIndex
 *  28   1  uint8   m_secondaryPlayerCarIndex 255 = no second player
 * ```
 */
object HeaderLayout {
    const val PACKET_FORMAT = 0
    const val GAME_YEAR = 2
    const val GAME_MAJOR_VERSION = 3
    const val GAME_MINOR_VERSION = 4
    const val PACKET_VERSION = 5
    const val PACKET_ID = 6
    const val SESSION_UID = 7
    const val SESSION_TIME = 15
    const val FRAME_IDENTIFIER = 19
    const val OVERALL_FRAME_IDENTIFIER = 23
    const val PLAYER_CAR_INDEX = 27
    const val SECONDARY_PLAYER_CAR_INDEX = 28
    const val SIZE = 29
}

internal object HeaderParser {
    /** Reads every header field except [PacketHeader.format], which the caller resolves. */
    fun parse(buf: ByteBuffer, out: PacketHeader) {
        out.packetFormat = buf.u16(HeaderLayout.PACKET_FORMAT)
        out.gameYear = buf.u8(HeaderLayout.GAME_YEAR)
        out.gameMajorVersion = buf.u8(HeaderLayout.GAME_MAJOR_VERSION)
        out.gameMinorVersion = buf.u8(HeaderLayout.GAME_MINOR_VERSION)
        out.packetVersion = buf.u8(HeaderLayout.PACKET_VERSION)
        out.packetId = buf.u8(HeaderLayout.PACKET_ID)
        out.sessionUid = buf.u64(HeaderLayout.SESSION_UID)
        out.sessionTime = buf.getFloat(HeaderLayout.SESSION_TIME)
        out.frameIdentifier = buf.u32(HeaderLayout.FRAME_IDENTIFIER)
        out.overallFrameIdentifier = buf.u32(HeaderLayout.OVERALL_FRAME_IDENTIFIER)
        out.playerCarIndex = buf.u8(HeaderLayout.PLAYER_CAR_INDEX)
        out.secondaryPlayerCarIndex = buf.u8(HeaderLayout.SECONDARY_PLAYER_CAR_INDEX)
    }
}
