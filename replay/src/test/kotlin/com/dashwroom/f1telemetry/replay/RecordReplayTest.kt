package com.dashwroom.f1telemetry.replay

import com.dashwroom.f1telemetry.core.ingest.DatagramSink
import com.dashwroom.f1telemetry.core.protocol.PacketFormat
import com.dashwroom.f1telemetry.replay.mock.MockTelemetryEmitter
import com.dashwroom.f1telemetry.replay.record.PacketRecorder
import com.dashwroom.f1telemetry.replay.record.RecordingReader
import com.dashwroom.f1telemetry.replay.record.ReplaySource
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.ByteBuffer

class RecordReplayTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun captureMock(frames: Int): Pair<java.io.File, List<ByteArray>> {
        val file = tmp.newFile("session.bin")
        val recorder = PacketRecorder()
        val original = mutableListOf<ByteArray>()
        recorder.start(file, nowNanos = 0)
        val sink = object : DatagramSink {
            override fun onDatagram(buffer: ByteBuffer, length: Int, receivedAtNanos: Long) {
                original += buffer.array().copyOfRange(buffer.arrayOffset(), buffer.arrayOffset() + length)
                recorder.onDatagram(buffer, length, receivedAtNanos)
            }
            override fun onBurstEnd() = Unit
        }
        val emitter = MockTelemetryEmitter(PacketFormat.F1_25, seed = 1)
        repeat(frames) {
            emitter.emitFrame(sink, it * 16_666_667L)
        }
        recorder.stop()
        assertThat(recorder.packetsDropped).isEqualTo(0)
        assertThat(recorder.packetsRecorded).isEqualTo(original.size.toLong())
        return file to original
    }

    @Test
    fun `recorded datagrams read back byte-identical with timestamps`() {
        val (file, original) = captureMock(frames = 300)
        RecordingReader(file).use { reader ->
            val buf = ByteArray(2048)
            var i = 0
            var lastTs = -1L
            while (true) {
                val n = reader.next(buf)
                if (n < 0) break
                assertThat(buf.copyOf(n)).isEqualTo(original[i])
                assertThat(reader.timestampNanos).isAtLeast(lastTs)
                lastTs = reader.timestampNanos
                i++
            }
            assertThat(i).isEqualTo(original.size)
            assertThat(lastTs).isEqualTo(299 * 16_666_667L)
        }
    }

    @Test
    fun `replay source delivers the capture in order`() = runBlocking {
        val (file, original) = captureMock(frames = 120) // 2 s of data
        val replayed = mutableListOf<ByteArray>()
        var bursts = 0
        val sink = object : DatagramSink {
            override fun onDatagram(buffer: ByteBuffer, length: Int, receivedAtNanos: Long) {
                replayed += buffer.array().copyOf(length)
            }
            override fun onBurstEnd() { bursts++ }
        }
        val job = launch(Dispatchers.IO) { ReplaySource(file, speed = 20f, loop = false).run(sink) }
        withTimeout(10_000) { job.join() }
        assertThat(replayed.size).isEqualTo(original.size)
        for (k in original.indices) assertThat(replayed[k]).isEqualTo(original[k])
        assertThat(bursts).isAtLeast(119) // one burst per recorded frame
    }
}
