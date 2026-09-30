package com.dashwroom.f1telemetry.core.ingest

import com.dashwroom.f1telemetry.core.parser.PacketParser
import com.dashwroom.f1telemetry.core.state.TelemetryStore
import java.nio.ByteBuffer

/**
 * receive → stats → (tap) → coalesce → parse → store. Single-threaded: every call must come from
 * the ingest thread. Allocation-free per datagram.
 */
class TelemetryPipeline(
    private val stats: IngestStats,
    private val store: TelemetryStore,
) : DatagramSink {
    private val parser = PacketParser()
    private val coalescer = DatagramCoalescer(stats, ::ingest)

    /** Raw-datagram observer (the recorder). May be swapped from any thread. */
    @Volatile var tap: DatagramTap? = null

    override fun onDatagram(buffer: ByteBuffer, length: Int, receivedAtNanos: Long) {
        stats.onReceived(buffer, length, receivedAtNanos)
        tap?.onDatagram(buffer, length, receivedAtNanos)
        coalescer.offer(buffer, length, receivedAtNanos)
    }

    override fun onBurstEnd() {
        coalescer.flush()
    }

    override fun onSender(address: String) {
        if (stats.sender != address) stats.sender = address
    }

    /** Call on the ingest thread with no source running. */
    fun reset() {
        coalescer.clear()
        stats.reset()
        store.reset()
    }

    private fun ingest(buffer: ByteBuffer, length: Int, receivedAtNanos: Long) {
        val packet = parser.parse(buffer, length)
        if (packet == null) {
            stats.onParseResult(parser.header.packetId, parser.lastResult, parser.header.packetFormat)
            return
        }
        store.apply(packet, buffer, length, receivedAtNanos)
    }
}
