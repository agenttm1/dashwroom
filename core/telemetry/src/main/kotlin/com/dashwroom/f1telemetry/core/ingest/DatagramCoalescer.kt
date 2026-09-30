package com.dashwroom.f1telemetry.core.ingest

import com.dashwroom.f1telemetry.core.parser.HeaderLayout
import com.dashwroom.f1telemetry.core.protocol.PacketId
import com.dashwroom.f1telemetry.core.protocol.PacketSizes
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Primitive-typed callback. A Kotlin function type `(ByteBuffer, Int, Long) -> Unit` would box the
 * Int and Long on every call — 40 bytes per datagram on the hot path.
 */
fun interface DatagramHandler {
    fun handle(buffer: ByteBuffer, length: Int, nanos: Long)
}

/**
 * "Keep only the newest packet of each type" while a burst is drained from the socket.
 *
 * Snapshot packets (telemetry, status, lap data, motion, …) fully describe current state, so if
 * several of the same type are queued only the newest matters and older ones are skipped. Packets
 * that are *not* snapshots — events, per-car session history and tyre sets, final classification —
 * are forwarded immediately in arrival order and never dropped.
 *
 * Pre-allocates one [PacketSizes.MAX_DATAGRAM] slot per packet id; never allocates afterwards.
 */
class DatagramCoalescer(
    private val stats: IngestStats,
    private val downstream: DatagramHandler,
) {
    private val slotBytes = Array(PacketId.COUNT) { ByteArray(PacketSizes.MAX_DATAGRAM) }
    private val slotBuffers = Array(PacketId.COUNT) { ByteBuffer.wrap(slotBytes[it]).order(ByteOrder.LITTLE_ENDIAN) }
    private val slotLength = IntArray(PacketId.COUNT)
    private val slotNanos = LongArray(PacketId.COUNT)
    private val pending = BooleanArray(PacketId.COUNT)
    private var anyPending = false

    fun offer(buffer: ByteBuffer, length: Int, nanos: Long) {
        if (length < PacketSizes.HEADER || length > PacketSizes.MAX_DATAGRAM) {
            downstream.handle(buffer, length, nanos)
            return
        }
        val id = buffer.get(HeaderLayout.PACKET_ID).toInt() and 0xFF
        if (id >= PacketId.COUNT || !isSnapshot(id)) {
            downstream.handle(buffer, length, nanos)
            return
        }
        if (pending[id]) stats.onCoalesced()
        System.arraycopy(buffer.array(), buffer.arrayOffset(), slotBytes[id], 0, length)
        slotLength[id] = length
        slotNanos[id] = nanos
        pending[id] = true
        anyPending = true
    }

    fun flush() {
        if (!anyPending) return
        anyPending = false
        for (id in FLUSH_ORDER) {
            if (!pending[id]) continue
            pending[id] = false
            downstream.handle(slotBuffers[id], slotLength[id], slotNanos[id])
        }
    }

    fun clear() {
        pending.fill(false)
        anyPending = false
    }

    companion object {
        fun isSnapshot(id: Int): Boolean = when (id) {
            PacketId.EVENT, PacketId.FINAL_CLASSIFICATION, PacketId.SESSION_HISTORY, PacketId.TYRE_SETS -> false
            else -> true
        }

        /** Identity/session info first so per-car packets land on up-to-date context. */
        private val FLUSH_ORDER = intArrayOf(
            PacketId.SESSION, PacketId.PARTICIPANTS, PacketId.LOBBY_INFO, PacketId.CAR_SETUPS,
            PacketId.LAP_DATA, PacketId.CAR_STATUS, PacketId.CAR_DAMAGE, PacketId.CAR_TELEMETRY,
            PacketId.CAR_TELEMETRY_2, PacketId.MOTION, PacketId.MOTION_EX, PacketId.TIME_TRIAL,
            PacketId.LAP_POSITIONS,
        )
    }
}
