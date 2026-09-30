package com.dashwroom.f1telemetry.core.ingest

import com.dashwroom.f1telemetry.core.Allocations
import com.dashwroom.f1telemetry.core.PacketFixtures
import com.dashwroom.f1telemetry.core.encodeAny
import com.dashwroom.f1telemetry.core.packet.F1Packet
import com.dashwroom.f1telemetry.core.parser.PacketParser
import com.dashwroom.f1telemetry.core.protocol.PacketFormat
import com.dashwroom.f1telemetry.core.state.HotTelemetry
import com.dashwroom.f1telemetry.core.state.TelemetryStore
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.nio.ByteBuffer

/**
 * The 60 Hz path must not allocate. Escape analysis is disabled for tests (see build.gradle.kts)
 * so HotSpot can't hide allocations that ART — which has no escape analysis — would perform.
 */
class ZeroAllocationTest {

    /** One frame's worth of 60 Hz packets (Motion, Lap Data, Car Telemetry, Car Status), same session. */
    private fun frameSet(format: PacketFormat): List<ByteBuffer> {
        val f = PacketFixtures(format.wireValue)
        val packets: List<F1Packet> = listOf(f.motion(format), f.lapData(format), f.telemetry(format), f.status(format))
        packets.forEach {
            it.header.sessionUid = 77L
            it.header.playerCarIndex = 3
        }
        return packets.map { encodeAny(it) }
    }

    @Test
    fun `parsing a full 60Hz packet set allocates nothing`() {
        for (format in PacketFormat.entries) {
            val set = frameSet(format)
            val parser = PacketParser()
            repeat(20_000) { for (b in set) parser.parse(b, b.capacity()) } // warm up / JIT
            val bytes = Allocations.measure {
                for (i in 0 until 10_000) for (j in set.indices) {
                    val b = set[j]
                    parser.parse(b, b.capacity())
                }
            }
            assertThat(bytes).isEqualTo(0L)
        }
    }

    @Test
    fun `full ingest pipeline for 60Hz packets allocates nothing`() {
        for (format in PacketFormat.entries) {
            val set = frameSet(format)
            val stats = IngestStats()
            val pipeline = TelemetryPipeline(stats, TelemetryStore(HotTelemetry()))
            var nanos = 1L
            repeat(20_000) {
                for (b in set) pipeline.onDatagram(b, b.capacity(), nanos++)
                pipeline.onBurstEnd()
            }
            val bytes = Allocations.measure {
                for (i in 0 until 10_000) {
                    for (j in set.indices) {
                        val b = set[j]
                        pipeline.onDatagram(b, b.capacity(), nanos++)
                    }
                    pipeline.onBurstEnd()
                }
            }
            assertThat(bytes).isEqualTo(0L)
            assertThat(stats.totalSizeMismatches()).isEqualTo(0)
        }
    }
}
