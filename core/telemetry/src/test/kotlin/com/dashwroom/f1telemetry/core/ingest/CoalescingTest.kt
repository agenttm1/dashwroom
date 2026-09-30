package com.dashwroom.f1telemetry.core.ingest

import com.dashwroom.f1telemetry.core.PacketFixtures
import com.dashwroom.f1telemetry.core.encodeAny
import com.dashwroom.f1telemetry.core.packet.EventCode
import com.dashwroom.f1telemetry.core.protocol.PacketFormat
import com.dashwroom.f1telemetry.core.state.HotTelemetry
import com.dashwroom.f1telemetry.core.state.TelemetryStore
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CoalescingTest {
    private val fixtures = PacketFixtures()
    private val hot = HotTelemetry()
    private val stats = IngestStats()
    private val pipeline = TelemetryPipeline(stats, TelemetryStore(hot))

    private fun telemetryWithSpeed(speed: Int) = fixtures.telemetry(PacketFormat.F1_25).apply {
        header.sessionUid = 9
        header.playerCarIndex = 2
        cars[2].speedKph = speed
    }.let(::encodeAny)

    @Test
    fun `a drained burst keeps only the newest snapshot packet of each type`() {
        val event = fixtures.event(PacketFormat.F1_25, EventCode.OVERTAKE).apply { header.sessionUid = 9 }.let(::encodeAny)
        val tap = mutableListOf<Int>()
        pipeline.tap = DatagramTap { b, _, _ -> tap += b.get(6).toInt() }

        pipeline.onDatagram(telemetryWithSpeed(100), 1352, 1)
        pipeline.onDatagram(event, 45, 2)
        pipeline.onDatagram(telemetryWithSpeed(200), 1352, 3)
        pipeline.onDatagram(telemetryWithSpeed(300), 1352, 4)
        assertThat(hot.speedKph).isEqualTo(0) // nothing snapshot-y applied until the burst ends
        pipeline.onBurstEnd()

        assertThat(hot.speedKph).isEqualTo(300)
        assertThat(stats.coalesced).isEqualTo(2)
        assertThat(stats.received(6)).isEqualTo(3) // stats and the recorder still see every datagram
        assertThat(tap).containsExactly(6, 3, 6, 6).inOrder()
    }

    @Test
    fun `separate bursts are each applied`() {
        pipeline.onDatagram(telemetryWithSpeed(111), 1352, 1)
        pipeline.onBurstEnd()
        assertThat(hot.speedKph).isEqualTo(111)
        pipeline.onDatagram(telemetryWithSpeed(222), 1352, 2)
        pipeline.onBurstEnd()
        assertThat(hot.speedKph).isEqualTo(222)
        assertThat(stats.coalesced).isEqualTo(0)
    }
}
