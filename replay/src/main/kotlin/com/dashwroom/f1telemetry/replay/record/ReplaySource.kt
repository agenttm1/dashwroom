package com.dashwroom.f1telemetry.replay.record

import com.dashwroom.f1telemetry.core.ingest.DatagramSink
import com.dashwroom.f1telemetry.core.model.SourceKind
import com.dashwroom.f1telemetry.core.protocol.PacketSizes
import com.dashwroom.f1telemetry.core.source.PacketSource
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.locks.LockSupport

/**
 * Plays a `.bin` capture back with its original timing (scaled by [speed]), feeding the exact
 * recorded bytes through the normal pipeline. Datagrams recorded within [BURST_GAP_NANOS] of each
 * other are delivered as one burst, just like a socket drain.
 */
class ReplaySource(
    private val file: File,
    private val speed: Float = 1f,
    private val loop: Boolean = true,
) : PacketSource {
    override val kind = SourceKind.REPLAY
    override val description = "Replay · ${file.name}"

    override suspend fun run(sink: DatagramSink) {
        require(speed > 0f) { "speed must be positive" }
        val bytes = ByteArray(PacketSizes.MAX_DATAGRAM)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        sink.onSender("replay")
        do {
            RecordingReader(file).use { reader ->
                val start = System.nanoTime()
                var previousTs = Long.MIN_VALUE
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val length = reader.next(bytes)
                    if (length < 0) break
                    val ts = reader.timestampNanos
                    if (previousTs != Long.MIN_VALUE && ts - previousTs > BURST_GAP_NANOS) sink.onBurstEnd()
                    previousTs = ts
                    waitUntil(start + (ts / speed).toLong())
                    sink.onDatagram(buffer, length, System.nanoTime())
                }
                sink.onBurstEnd()
            }
        } while (loop)
    }

    private suspend fun waitUntil(target: Long) {
        while (true) {
            val wait = target - System.nanoTime()
            if (wait <= 0) return
            currentCoroutineContext().ensureActive()
            LockSupport.parkNanos(minOf(wait, MAX_PARK_NANOS))
        }
    }

    private companion object {
        const val BURST_GAP_NANOS = 1_000_000L
        const val MAX_PARK_NANOS = 100_000_000L
    }
}
