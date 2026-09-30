package com.dashwroom.f1telemetry.core.parser

import com.dashwroom.f1telemetry.core.PacketFixtures
import com.dashwroom.f1telemetry.core.encodeAny
import com.dashwroom.f1telemetry.core.packet.EventCode
import com.dashwroom.f1telemetry.core.packet.EventPacket
import com.dashwroom.f1telemetry.core.packet.F1Packet
import com.dashwroom.f1telemetry.core.protocol.PacketFormat
import com.dashwroom.f1telemetry.core.protocol.PacketSizes
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * encode (sequential, struct order) → parse (absolute offsets) → encode again must reproduce the
 * exact bytes. Any field the parser reads from the wrong offset or width changes the output.
 * Also proves every encoded packet is exactly the size the spec states.
 */
@RunWith(Parameterized::class)
class RoundTripTest(private val format: PacketFormat) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun formats() = PacketFormat.entries.toList()
    }

    private val fixtures = PacketFixtures(seed = format.wireValue)
    private val parser = PacketParser()

    private fun roundTrip(original: F1Packet) {
        val bytes = encodeAny(original)
        assertThat(bytes.capacity()).isEqualTo(PacketSizes.expected(format, original.packetId))
        val parsed = parser.parse(bytes, bytes.capacity())
        assertThat(parser.lastResult).isEqualTo(ParseResult.OK)
        val again = encodeAny(parsed!!)
        assertThat(again.array()).isEqualTo(bytes.array())
    }

    @Test fun motion() = repeat(20) { roundTrip(fixtures.motion(format)) }

    @Test fun session() = repeat(20) { roundTrip(fixtures.session(format)) }

    @Test fun lapData() = repeat(20) { roundTrip(fixtures.lapData(format)) }

    @Test fun participants() = repeat(20) { roundTrip(fixtures.participants(format)) }

    @Test fun carTelemetry() = repeat(20) { roundTrip(fixtures.telemetry(format)) }

    @Test fun carStatus() = repeat(20) { roundTrip(fixtures.status(format)) }

    @Test
    fun everyEventCode() {
        val codes = listOf(
            EventCode.SESSION_STARTED, EventCode.SESSION_ENDED, EventCode.FASTEST_LAP, EventCode.RETIREMENT,
            EventCode.DRS_ENABLED, EventCode.DRS_DISABLED, EventCode.TEAM_MATE_IN_PITS, EventCode.CHEQUERED_FLAG,
            EventCode.RACE_WINNER, EventCode.PENALTY, EventCode.SPEED_TRAP, EventCode.START_LIGHTS,
            EventCode.LIGHTS_OUT, EventCode.DRIVE_THROUGH_SERVED, EventCode.STOP_GO_SERVED, EventCode.FLASHBACK,
            EventCode.BUTTON_STATUS, EventCode.RED_FLAG, EventCode.OVERTAKE, EventCode.SAFETY_CAR,
            EventCode.COLLISION, EventCode.PARTIAL_MODE_ENABLED, EventCode.PARTIAL_MODE_DISABLED,
            EventCode.OVERTAKE_MODE_ENABLED, EventCode.OVERTAKE_MODE_DISABLED,
        )
        for (code in codes) repeat(5) { roundTrip(fixtures.event(format, code)) }
    }

    @Test
    fun `collision severity only exists in 2026`() {
        val event = fixtures.event(format, EventCode.COLLISION).apply { collisionSeverity = 2 }
        val bytes = encodeAny(event)
        val parsed = parser.parse(bytes, bytes.capacity()) as EventPacket
        assertThat(parsed.collisionSeverity).isEqualTo(if (format == PacketFormat.F1_25_SEASON_2026) 2 else -1)
    }

    @Test
    fun `event codes pack and unpack`() {
        assertThat(EventCode.unpack(EventCode.FASTEST_LAP)).isEqualTo("FTLP")
        assertThat(EventCode.pack("SCAR")).isEqualTo(EventCode.SAFETY_CAR)
    }
}
