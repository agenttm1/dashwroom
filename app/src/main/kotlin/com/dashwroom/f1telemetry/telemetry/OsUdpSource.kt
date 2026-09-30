package com.dashwroom.f1telemetry.telemetry

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
import android.system.StructTimeval
import android.os.Build
import com.dashwroom.f1telemetry.core.ingest.DatagramSink
import com.dashwroom.f1telemetry.core.model.SourceKind
import com.dashwroom.f1telemetry.core.protocol.PacketSizes
import com.dashwroom.f1telemetry.core.source.PacketSource
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.FileDescriptor
import java.io.IOException
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Live UDP receiver built directly on `android.system.Os` so the steady-state loop allocates
 * nothing: `DatagramSocket`/`DatagramChannel` create a sender-address object per datagram.
 *
 * Loop: poll (≤250 ms, so cancellation is prompt) → drain every queued datagram with
 * non-blocking reads → end-of-burst (the pipeline keeps the newest packet of each type).
 * The sender's address is looked up at most once a second, which is the only allocation.
 */
class OsUdpSource(
    private val port: Int,
    private val receiveBufferBytes: Int = 512 * 1024,
) : PacketSource {
    override val kind = SourceKind.LIVE
    override val description = "UDP port $port"

    override suspend fun run(sink: DatagramSink) {
        val fd = open()
        try {
            val bytes = ByteArray(PacketSizes.MAX_DATAGRAM)
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val pollFds = arrayOf(StructPollfd().apply {
                this.fd = fd
                events = OsConstants.POLLIN.toShort()
            })
            var nextSenderCheck = 0L
            while (true) {
                currentCoroutineContext().ensureActive()
                if (!poll(pollFds, POLL_TIMEOUT_MS)) continue
                var drained = 0
                do {
                    val now = System.nanoTime()
                    val length: Int
                    if (now >= nextSenderCheck) {
                        val from = InetSocketAddress(0)
                        length = receive(fd, bytes, from)
                        if (length >= 0) from.address?.hostAddress?.let(sink::onSender)
                        nextSenderCheck = now + SENDER_CHECK_INTERVAL_NANOS
                    } else {
                        length = receive(fd, bytes, null)
                    }
                    if (length < 0) break
                    if (length > 0) sink.onDatagram(buffer, length, now)
                    drained++
                } while (drained < MAX_DRAIN && poll(pollFds, 0))
                sink.onBurstEnd()
            }
        } finally {
            runCatching { Os.close(fd) }
        }
    }

    private fun open(): FileDescriptor {
        val fd = Os.socket(OsConstants.AF_INET, OsConstants.SOCK_DGRAM, OsConstants.IPPROTO_UDP)
        try {
            Os.setsockoptInt(fd, OsConstants.SOL_SOCKET, OsConstants.SO_RCVBUF, receiveBufferBytes)
            makeReadsNonBlocking(fd)
            Os.bind(fd, Inet4Address.getByName("0.0.0.0"), port)
            return fd
        } catch (e: ErrnoException) {
            runCatching { Os.close(fd) }
            if (e.errno == OsConstants.EADDRINUSE) throw IOException("Port $port is already in use by another app")
            throw IOException("Could not open UDP port $port: ${e.message}")
        }
    }

    /**
     * poll() does the waiting; reads must never block even if a datagram vanishes between poll
     * and read (e.g. a UDP checksum failure). API 30+: O_NONBLOCK. API 29: a short receive
     * timeout. API 26-28: neither call is public, so a read could at worst wait for the next
     * datagram (~16 ms at 60 Hz) — harmless for correctness.
     */
    private fun makeReadsNonBlocking(fd: FileDescriptor) {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                Os.fcntlInt(fd, OsConstants.F_SETFL, Os.fcntlInt(fd, OsConstants.F_GETFL, 0) or OsConstants.O_NONBLOCK)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                Os.setsockoptTimeval(fd, OsConstants.SOL_SOCKET, OsConstants.SO_RCVTIMEO, StructTimeval.fromMillis(RECEIVE_TIMEOUT_MS))
        }
    }

    /** Non-blocking read; -1 when the queue turned out to be empty. */
    private fun receive(fd: FileDescriptor, bytes: ByteArray, from: InetSocketAddress?): Int = try {
        Os.recvfrom(fd, bytes, 0, bytes.size, 0, from)
    } catch (e: ErrnoException) {
        if (e.errno == OsConstants.EAGAIN || e.errno == OsConstants.EINTR) -1 else throw IOException("recv failed: ${e.message}")
    }

    /** True when a datagram is waiting. EINTR and EAGAIN simply mean "nothing yet". */
    private fun poll(fds: Array<StructPollfd>, timeoutMs: Int): Boolean {
        fds[0].revents = 0
        return try {
            Os.poll(fds, timeoutMs) > 0 && (fds[0].revents.toInt() and OsConstants.POLLIN) != 0
        } catch (e: ErrnoException) {
            if (e.errno == OsConstants.EINTR) false else throw IOException("poll failed: ${e.message}")
        }
    }

    private companion object {
        const val POLL_TIMEOUT_MS = 250
        const val MAX_DRAIN = 512
        const val SENDER_CHECK_INTERVAL_NANOS = 1_000_000_000L
        const val RECEIVE_TIMEOUT_MS = 20L
    }
}
