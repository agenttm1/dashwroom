package com.dashwroom.f1telemetry.core.parser

import com.dashwroom.f1telemetry.core.packet.CarStatusPacket
import com.dashwroom.f1telemetry.core.packet.CarTelemetryPacket
import com.dashwroom.f1telemetry.core.packet.EventPacket
import com.dashwroom.f1telemetry.core.packet.F1Packet
import com.dashwroom.f1telemetry.core.packet.LapDataPacket
import com.dashwroom.f1telemetry.core.packet.MotionPacket
import com.dashwroom.f1telemetry.core.packet.PacketHeader
import com.dashwroom.f1telemetry.core.packet.ParticipantsPacket
import com.dashwroom.f1telemetry.core.packet.SessionPacket
import com.dashwroom.f1telemetry.core.protocol.PacketFormat
import com.dashwroom.f1telemetry.core.protocol.PacketId
import com.dashwroom.f1telemetry.core.protocol.PacketSizes
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Why the last [PacketParser.parse] call returned what it did. */
enum class ParseResult {
    OK,
    TOO_SHORT,
    UNKNOWN_FORMAT,
    UNKNOWN_PACKET_ID,
    SIZE_MISMATCH,

    /** A valid packet type this build doesn't decode yet; counted, then skipped. */
    NOT_DECODED,
}

/**
 * Decodes one datagram into a reusable [F1Packet]. Not thread-safe: one parser per ingest thread.
 *
 * Zero-allocation contract: after construction, `parse` allocates nothing (the only exception is
 * decoding a participant name, which happens only when a name actually changes).
 */
class PacketParser {
    /** Header of the last datagram, filled whenever [lastResult] is past [ParseResult.TOO_SHORT]. */
    val header = PacketHeader()
    var lastResult: ParseResult = ParseResult.OK
        private set

    private val motion = MotionPacket()
    private val session = SessionPacket()
    private val lapData = LapDataPacket()
    private val event = EventPacket()
    private val participants = ParticipantsPacket()
    private val carTelemetry = CarTelemetryPacket()
    private val carStatus = CarStatusPacket()

    /**
     * @param buffer little-endian buffer whose datagram starts at index 0.
     * @return the decoded packet (valid until the next call), or null — see [lastResult].
     */
    fun parse(buffer: ByteBuffer, length: Int): F1Packet? {
        check(buffer.order() == ByteOrder.LITTLE_ENDIAN) { "F1 UDP data is little-endian" }
        if (length < PacketSizes.HEADER) return fail(ParseResult.TOO_SHORT)
        HeaderParser.parse(buffer, header)
        val format = PacketFormat.fromWire(header.packetFormat) ?: return fail(ParseResult.UNKNOWN_FORMAT)
        header.format = format
        val expected = PacketSizes.expected(format, header.packetId)
        if (expected == PacketSizes.NOT_IN_FORMAT) return fail(ParseResult.UNKNOWN_PACKET_ID)
        if (length != expected) return fail(ParseResult.SIZE_MISMATCH)

        val packet: F1Packet = when (header.packetId) {
            PacketId.MOTION -> motion.also { it.header.copyFrom(header); MotionParser.parse(buffer, it) }
            PacketId.SESSION -> session.also { it.header.copyFrom(header); SessionParser.parse(buffer, it) }
            PacketId.LAP_DATA -> lapData.also { it.header.copyFrom(header); LapDataParser.parse(buffer, it) }
            PacketId.EVENT -> event.also { it.header.copyFrom(header); EventParser.parse(buffer, it) }
            PacketId.PARTICIPANTS ->
                participants.also { it.header.copyFrom(header); ParticipantsParser.parse(buffer, it) }
            PacketId.CAR_TELEMETRY ->
                carTelemetry.also { it.header.copyFrom(header); CarTelemetryParser.parse(buffer, it) }
            PacketId.CAR_STATUS -> carStatus.also { it.header.copyFrom(header); CarStatusParser.parse(buffer, it) }
            else -> return fail(ParseResult.NOT_DECODED)
        }
        lastResult = ParseResult.OK
        return packet
    }

    private fun fail(result: ParseResult): F1Packet? {
        lastResult = result
        return null
    }
}
