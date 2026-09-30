package com.dashwroom.f1telemetry.benchmark

import com.dashwroom.f1telemetry.core.ingest.DatagramSink
import com.dashwroom.f1telemetry.core.ingest.IngestStats
import com.dashwroom.f1telemetry.core.ingest.TelemetryPipeline
import com.dashwroom.f1telemetry.core.parser.PacketParser
import com.dashwroom.f1telemetry.core.protocol.PacketFormat
import com.dashwroom.f1telemetry.core.protocol.PacketId
import com.dashwroom.f1telemetry.core.state.HotTelemetry
import com.dashwroom.f1telemetry.core.state.TelemetryStore
import com.dashwroom.f1telemetry.replay.mock.MockTelemetryEmitter
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.Level
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Param
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.infra.Blackhole
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit

/**
 * "Parsing one full 60 Hz packet set must take well under 1 ms and allocate nothing."
 *
 * A 60 Hz set is everything the game sends on one frame at the menu rate: Motion, Lap Data,
 * Car Telemetry and Car Status (2026 also sends Car Telemetry 2 — counted, not yet decoded).
 * Packet contents are captured from the mock race mid-session, so they're realistic, not zeros.
 * Read `gc.alloc.rate.norm` (bytes/op) from the GC profiler for the allocation figure.
 */
@State(Scope.Thread)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
open class ParserBenchmark {
    @Param("2025", "2026")
    var format: Int = 2025

    private lateinit var frameSet: Array<ByteBuffer>
    private lateinit var lengths: IntArray
    private lateinit var slowSet: Array<ByteBuffer>
    private val parser = PacketParser()
    private lateinit var pipeline: TelemetryPipeline
    private var nanos = 0L

    @Setup(Level.Trial)
    fun setup() {
        val fmt = PacketFormat.fromWire(format)!!
        val latest = HashMap<Int, ByteArray>()
        val capture = object : DatagramSink {
            override fun onDatagram(buffer: ByteBuffer, length: Int, receivedAtNanos: Long) {
                latest[buffer.get(6).toInt()] = buffer.array().copyOf(length)
            }
            override fun onBurstEnd() = Unit
        }
        val emitter = MockTelemetryEmitter(fmt, seed = 1)
        repeat(60 * 45) { emitter.emitFrame(capture, 0) } // 45 s in: racing, all fields populated
        fun buf(id: Int) = ByteBuffer.wrap(latest.getValue(id)).order(ByteOrder.LITTLE_ENDIAN)
        frameSet = arrayOf(buf(PacketId.MOTION), buf(PacketId.LAP_DATA), buf(PacketId.CAR_TELEMETRY), buf(PacketId.CAR_STATUS))
        lengths = IntArray(frameSet.size) { frameSet[it].capacity() }
        slowSet = arrayOf(buf(PacketId.SESSION), buf(PacketId.PARTICIPANTS))
        pipeline = TelemetryPipeline(IngestStats(), TelemetryStore(HotTelemetry()))
    }

    /** Decode only: header validation + body parse of the four 60 Hz packets. */
    @Benchmark
    fun parseFrameSet(bh: Blackhole) {
        for (i in frameSet.indices) bh.consume(parser.parse(frameSet[i], lengths[i]))
    }

    /** Everything the ingest thread does per frame: stats, loss tracking, coalescing, parse, state update. */
    @Benchmark
    fun ingestFrameSet() {
        for (i in frameSet.indices) pipeline.onDatagram(frameSet[i], lengths[i], nanos++)
        pipeline.onBurstEnd()
    }

    /** The two biggest low-rate packets (Session 2 Hz, Participants 0.2 Hz), decode only. */
    @Benchmark
    fun parseSessionAndParticipants(bh: Blackhole) {
        for (b in slowSet) bh.consume(parser.parse(b, b.capacity()))
    }
}
