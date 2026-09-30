package com.dashwroom.f1telemetry.replay

import com.dashwroom.f1telemetry.core.ingest.DatagramSink
import com.dashwroom.f1telemetry.core.ingest.IngestStats
import com.dashwroom.f1telemetry.core.ingest.TelemetryPipeline
import com.dashwroom.f1telemetry.core.parser.PacketParser
import com.dashwroom.f1telemetry.core.parser.ParseResult
import com.dashwroom.f1telemetry.core.protocol.PacketFormat
import com.dashwroom.f1telemetry.core.protocol.PacketId
import com.dashwroom.f1telemetry.core.state.HotTelemetry
import com.dashwroom.f1telemetry.core.state.TelemetryStore
import com.dashwroom.f1telemetry.replay.mock.MockTelemetryEmitter
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.lang.management.ManagementFactory
import java.nio.ByteBuffer

class MockEmitterTest {

    @Test
    fun `every mock datagram parses cleanly in both formats`() {
        for (format in PacketFormat.entries) {
            val emitter = MockTelemetryEmitter(format, seed = 3)
            val parser = PacketParser()
            val results = mutableMapOf<ParseResult, Int>()
            val ids = mutableMapOf<Int, Int>()
            val sink = object : DatagramSink {
                override fun onDatagram(buffer: ByteBuffer, length: Int, receivedAtNanos: Long) {
                    parser.parse(buffer, length)
                    results.merge(parser.lastResult, 1, Int::plus)
                    ids.merge(parser.header.packetId, 1, Int::plus)
                }
                override fun onBurstEnd() = Unit
            }
            repeat(60 * 90) { emitter.emitFrame(sink, it.toLong()) } // 90 s of racing
            assertThat(results.keys).containsExactly(ParseResult.OK)
            assertThat(ids[PacketId.CAR_TELEMETRY]).isEqualTo(60 * 90)
            assertThat(ids[PacketId.SESSION]).isIn(com.google.common.collect.Range.closed(180, 181))
            assertThat(ids[PacketId.PARTICIPANTS]).isIn(com.google.common.collect.Range.closed(18, 19))
            assertThat(ids).containsKey(PacketId.EVENT)
        }
    }

    @Test
    fun `mock through the real pipeline populates session and hot state`() {
        val hot = HotTelemetry()
        val store = TelemetryStore(hot)
        val pipeline = TelemetryPipeline(IngestStats(), store)
        val emitter = MockTelemetryEmitter(PacketFormat.F1_25_SEASON_2026, seed = 11)
        var topSpeed = 0
        repeat(60 * 30) {
            emitter.emitFrame(pipeline, it * 16_666_667L)
            topSpeed = maxOf(topSpeed, hot.speedKph)
        }
        val session = store.session.value
        assertThat(session.info!!.trackName).isEqualTo("Silverstone")
        assertThat(session.participants).hasSize(22)
        assertThat(session.participants.map { it.teamName }).contains("Cadillac '26")
        assertThat(hot.playerCarIndex).isEqualTo(5)
        assertThat(topSpeed).isGreaterThan(250)
        assertThat(hot.gear).isAtLeast(1)
        assertThat(hot.maxRpm).isEqualTo(13_000)
        assertThat(hot.activeCarsMask).isEqualTo((1 shl 22) - 1)
    }

    @Test
    fun `emitting a frame allocates nothing`() {
        val bean = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        val emitter = MockTelemetryEmitter(PacketFormat.F1_25, seed = 5)
        val nullSink = object : DatagramSink {
            override fun onDatagram(buffer: ByteBuffer, length: Int, receivedAtNanos: Long) = Unit
            override fun onBurstEnd() = Unit
        }
        repeat(60 * 20) { emitter.emitFrame(nullSink, 0) } // warm-up, includes the start
        val thread = Thread.currentThread().id
        val before = bean.getThreadAllocatedBytes(thread)
        repeat(60 * 60) { emitter.emitFrame(nullSink, 0) }
        val allocated = bean.getThreadAllocatedBytes(thread) - before
        assertThat(allocated).isLessThan(1_024L)
    }
}
