package com.dashwroom.f1telemetry.core.source

import com.dashwroom.f1telemetry.core.ingest.DatagramSink
import com.dashwroom.f1telemetry.core.model.SourceKind
import com.dashwroom.f1telemetry.core.protocol.PacketSizes
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.net.InetSocketAddress
import java.net.StandardSocketOptions
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.DatagramChannel
import java.nio.channels.SelectionKey
import java.nio.channels.Selector

/**
 * Portable JVM UDP receiver (desktop tools and integration tests). On Android the app uses an
 * `android.system.Os`-based receiver instead, because `DatagramChannel.receive` allocates a
 * sender address object per datagram.
 *
 * Each wake-up drains every queued datagram, then signals end-of-burst so the coalescer keeps
 * only the newest packet of each type.
 */
class DatagramChannelSource(
    private val port: Int,
    private val receiveBufferBytes: Int = 512 * 1024,
) : PacketSource {
    override val kind = SourceKind.LIVE
    override val description = "UDP :$port"

    override suspend fun run(sink: DatagramSink) {
        val bytes = ByteArray(PacketSizes.MAX_DATAGRAM)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        DatagramChannel.open().use { channel ->
            channel.setOption(StandardSocketOptions.SO_REUSEADDR, true)
            channel.setOption(StandardSocketOptions.SO_RCVBUF, receiveBufferBytes)
            channel.bind(InetSocketAddress(port))
            channel.configureBlocking(false)
            Selector.open().use { selector ->
                channel.register(selector, SelectionKey.OP_READ)
                var lastSender: Any? = null
                while (true) {
                    currentCoroutineContext().ensureActive()
                    if (selector.select(POLL_MS) == 0) continue
                    selector.selectedKeys().clear()
                    while (true) {
                        buffer.clear()
                        val from = channel.receive(buffer) ?: break
                        val now = System.nanoTime()
                        if (from != lastSender) {
                            lastSender = from
                            sink.onSender((from as? InetSocketAddress)?.address?.hostAddress ?: from.toString())
                        }
                        sink.onDatagram(buffer, buffer.position(), now)
                    }
                    sink.onBurstEnd()
                }
            }
        }
    }

    private companion object {
        const val POLL_MS = 250L
    }
}
