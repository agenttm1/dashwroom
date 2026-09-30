package com.dashwroom.f1telemetry.core.parser

import com.dashwroom.f1telemetry.core.blankPacket
import com.dashwroom.f1telemetry.core.protocol.PacketFormat
import com.dashwroom.f1telemetry.core.protocol.PacketId
import com.dashwroom.f1telemetry.core.protocol.PacketSizes
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.random.Random

class ParserRobustnessTest {
    private val parser = PacketParser()

    private fun le(size: Int) = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)

    @Test
    fun `shorter than a header is rejected`() {
        assertThat(parser.parse(le(28), 28)).isNull()
        assertThat(parser.lastResult).isEqualTo(ParseResult.TOO_SHORT)
    }

    @Test
    fun `unknown packet formats are ignored, not parsed`() {
        for (format in listOf(2023, 2024, 2027, 0, 65535)) {
            val b = le(1352).putShort(0, format.toShort()).put(6, PacketId.CAR_TELEMETRY.toByte())
            assertThat(parser.parse(b, 1352)).isNull()
            assertThat(parser.lastResult).isEqualTo(ParseResult.UNKNOWN_FORMAT)
        }
    }

    @Test
    fun `wrong length for the format is rejected`() {
        // A 2026-sized telemetry packet claiming to be 2025.
        val b = le(1448).putShort(0, 2025.toShort()).put(6, PacketId.CAR_TELEMETRY.toByte())
        assertThat(parser.parse(b, 1448)).isNull()
        assertThat(parser.lastResult).isEqualTo(ParseResult.SIZE_MISMATCH)
    }

    @Test
    fun `car telemetry 2 is unknown in 2025 and counted but not decoded in 2026`() {
        val b25 = le(269).putShort(0, 2025.toShort()).put(6, PacketId.CAR_TELEMETRY_2.toByte())
        assertThat(parser.parse(b25, 269)).isNull()
        assertThat(parser.lastResult).isEqualTo(ParseResult.UNKNOWN_PACKET_ID)

        val b26 = blankPacket(PacketFormat.F1_25_SEASON_2026, PacketId.CAR_TELEMETRY_2)
        assertThat(parser.parse(b26, b26.capacity())).isNull()
        assertThat(parser.lastResult).isEqualTo(ParseResult.NOT_DECODED)
    }

    @Test
    fun `packet ids beyond the table are unknown`() {
        val b = le(100).putShort(0, 2025.toShort()).put(6, 200.toByte())
        assertThat(parser.parse(b, 100)).isNull()
        assertThat(parser.lastResult).isEqualTo(ParseResult.UNKNOWN_PACKET_ID)
    }

    @Test
    fun `later-phase packets with valid sizes are skipped gracefully`() {
        for (id in listOf(PacketId.CAR_SETUPS, PacketId.CAR_DAMAGE, PacketId.SESSION_HISTORY, PacketId.TYRE_SETS,
            PacketId.MOTION_EX, PacketId.LAP_POSITIONS, PacketId.FINAL_CLASSIFICATION, PacketId.LOBBY_INFO,
            PacketId.TIME_TRIAL)) {
            for (format in PacketFormat.entries) {
                val b = blankPacket(format, id)
                assertThat(parser.parse(b, b.capacity())).isNull()
                assertThat(parser.lastResult).isEqualTo(ParseResult.NOT_DECODED)
            }
        }
    }

    @Test
    fun `random garbage never throws`() {
        val random = Random(99)
        repeat(20_000) {
            val size = random.nextInt(0, 2048)
            val bytes = random.nextBytes(size)
            if (size >= 7 && random.nextBoolean()) {
                // Make many of them look plausible so the body parsers run on noise too.
                val format = if (random.nextBoolean()) 2025 else 2026
                bytes[0] = (format and 0xFF).toByte()
                bytes[1] = (format ushr 8).toByte()
                bytes[6] = random.nextInt(0, 17).toByte()
            }
            parser.parse(ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN), size)
        }
        for (format in PacketFormat.entries) for (id in 0 until PacketId.COUNT) {
            if (PacketSizes.expected(format, id) == PacketSizes.NOT_IN_FORMAT) continue
            val b = blankPacket(format, id)
            val random2 = Random(id)
            for (i in 29 until b.capacity()) b.put(i, random2.nextInt().toByte())
            parser.parse(b, b.capacity())
        }
    }
}
