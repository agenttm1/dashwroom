package com.dashwroom.f1telemetry.core.parser

import com.dashwroom.f1telemetry.core.packet.PacketHeader
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class HeaderParserTest {
    /** Bytes written by hand at the offsets listed in the spec's PacketHeader table. */
    @Test
    fun `parses every header field at its spec offset`() {
        val bytes = byteArrayOf(
            0xE9.toByte(), 0x07, // 0  uint16 packetFormat = 2025 (0x07E9)
            25, // 2  gameYear
            1, // 3  gameMajorVersion
            17, // 4  gameMinorVersion
            1, // 5  packetVersion
            6, // 6  packetId = Car Telemetry
            0x08, 0x07, 0x06, 0x05, 0x04, 0x03, 0x02, 0xF1.toByte(), // 7 uint64 sessionUID = 0xF102030405060708
            0x00, 0x00, 0xC0.toByte(), 0x3F, // 15 float sessionTime = 1.5f
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFE.toByte(), // 19 uint32 frameIdentifier = 0xFEFFFFFF
            0x10, 0x00, 0x00, 0x80.toByte(), // 23 uint32 overallFrameIdentifier = 0x80000010
            19, // 27 playerCarIndex
            255.toByte(), // 28 secondaryPlayerCarIndex
        )
        assertThat(bytes.size).isEqualTo(HeaderLayout.SIZE)
        val header = PacketHeader()
        HeaderParser.parse(ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN), header)

        assertThat(header.packetFormat).isEqualTo(2025)
        assertThat(header.gameYear).isEqualTo(25)
        assertThat(header.gameMajorVersion).isEqualTo(1)
        assertThat(header.gameMinorVersion).isEqualTo(17)
        assertThat(header.packetVersion).isEqualTo(1)
        assertThat(header.packetId).isEqualTo(6)
        assertThat(java.lang.Long.toUnsignedString(header.sessionUid)).isEqualTo("17366446428893087496")
        assertThat(header.sessionTime).isEqualTo(1.5f)
        assertThat(header.frameIdentifier).isEqualTo(0xFEFFFFFFL)
        assertThat(header.overallFrameIdentifier).isEqualTo(0x80000010L)
        assertThat(header.playerCarIndex).isEqualTo(19)
        assertThat(header.secondaryPlayerCarIndex).isEqualTo(255)
    }
}
