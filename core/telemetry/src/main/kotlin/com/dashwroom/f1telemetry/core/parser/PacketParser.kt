package com.dashwroom.f1telemetry.core.parser

import com.dashwroom.f1telemetry.core.packet.CarDamagePacket
import com.dashwroom.f1telemetry.core.packet.CarStatusPacket
import com.dashwroom.f1telemetry.core.packet.CarTelemetry2Packet
import com.dashwroom.f1telemetry.core.packet.FinalClassificationPacket
import com.dashwroom.f1telemetry.core.packet.LapPositionsPacket
import com.dashwroom.f1telemetry.core.packet.MotionExPacket
import com.dashwroom.f1telemetry.core.packet.SessionHistoryPacket
import com.dashwroom.f1telemetry.core.packet.TyreSetsPacket
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

    /** A valid packet type this app doesn't decode (Car Setups, Lobby Info, Time Trial); counted, then skipped. */
    NOT_DECODED,
}

/**
 * Decodes one datagram into a reusable [F1Packet]. Not thread-safe: one parser per ingest thread.
 *
 * Zero-allocation contract: after construction, `parse` allocates nothing (the only exception is
 * decoding a participant name, which happens only when a name actually changes).
 *
 * Decoded: 0 Motion, 1 Session, 2 Lap Data, 3 Event, 4 Participants, 6 Car Telemetry, 7 Car Status,
 * 8 Final Classification, 10 Car Damage, 11 Session History, 12 Tyre Sets, 13 Motion Ex,
 * 15 Lap Positions, 16 Car Telemetry 2. Counted but skipped: 5 Car Setups, 9 Lobby Info, 14 Time Trial.
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
    private val finalClassification = FinalClassificationPacket()
    private val carDamage = CarDamagePacket()
    private val sessionHistory = SessionHistoryPacket()
    private val tyreSets = TyreSetsPacket()
    private val motionEx = MotionExPacket()
    private val lapPositions = LapPositionsPacket()
    private val carTelemetry2 = CarTelemetry2Packet()

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
            PacketId.FINAL_CLASSIFICATION ->
                finalClassification.also { it.header.copyFrom(header); FinalClassificationParser.parse(buffer, it) }
            PacketId.CAR_DAMAGE -> carDamage.also { it.header.copyFrom(header); CarDamageParser.parse(buffer, it) }
            PacketId.SESSION_HISTORY ->
                sessionHistory.also { it.header.copyFrom(header); SessionHistoryParser.parse(buffer, it) }
            PacketId.TYRE_SETS -> tyreSets.also { it.header.copyFrom(header); TyreSetsParser.parse(buffer, it) }
            PacketId.MOTION_EX -> motionEx.also { it.header.copyFrom(header); MotionExParser.parse(buffer, it) }
            PacketId.LAP_POSITIONS -> lapPositions.also { it.header.copyFrom(header); LapPositionsParser.parse(buffer, it) }
            PacketId.CAR_TELEMETRY_2 ->
                carTelemetry2.also { it.header.copyFrom(header); CarTelemetry2Parser.parse(buffer, it) }
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
