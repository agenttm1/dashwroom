package com.dashwroom.f1telemetry.core.state

import com.dashwroom.f1telemetry.core.parser.HeaderLayout
import com.dashwroom.f1telemetry.core.protocol.PacketFormat
import com.dashwroom.f1telemetry.core.protocol.PacketId
import com.dashwroom.f1telemetry.core.protocol.PacketSizes
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Latest raw bytes of every packet the slow (≤10 Hz) model is built from. Stashing is a plain
 * array copy — zero allocation on the 60 Hz path; decoding happens only when the model is
 * published. Session History and Tyre Sets arrive one car at a time, so they get a slot per car
 * and are only marked dirty when their bytes actually changed.
 */
internal class ColdStash {
    val buffers = Array(PacketId.COUNT) { ByteBuffer.wrap(ByteArray(PacketSizes.MAX_DATAGRAM)).order(ByteOrder.LITTLE_ENDIAN) }
    val lengths = IntArray(PacketId.COUNT)
    val present = BooleanArray(PacketId.COUNT)
    val dirty = BooleanArray(PacketId.COUNT)

    val historyBuffers = perCar(HISTORY_SIZE)
    val historyLengths = IntArray(PacketFormat.MAX_CARS)
    val historyDirty = BooleanArray(PacketFormat.MAX_CARS)

    val tyreSetBuffers = perCar(TYRE_SETS_SIZE)
    val tyreSetLengths = IntArray(PacketFormat.MAX_CARS)
    val tyreSetDirty = BooleanArray(PacketFormat.MAX_CARS)

    fun stash(packetId: Int, carIdx: Int, source: ByteBuffer, length: Int) {
        when (packetId) {
            PacketId.SESSION_HISTORY -> if (carIdx < PacketFormat.MAX_CARS && length <= HISTORY_SIZE) {
                if (copyIfChanged(source, length, historyBuffers[carIdx], historyLengths[carIdx])) {
                    historyLengths[carIdx] = length
                    historyDirty[carIdx] = true
                    dirty[packetId] = true
                }
            }
            PacketId.TYRE_SETS -> if (carIdx < PacketFormat.MAX_CARS && length <= TYRE_SETS_SIZE) {
                if (copyIfChanged(source, length, tyreSetBuffers[carIdx], tyreSetLengths[carIdx])) {
                    tyreSetLengths[carIdx] = length
                    tyreSetDirty[carIdx] = true
                    dirty[packetId] = true
                }
            }
            else -> {
                System.arraycopy(source.array(), source.arrayOffset(), buffers[packetId].array(), 0, length)
                lengths[packetId] = length
                present[packetId] = true
                dirty[packetId] = true
            }
        }
    }

    fun clear() {
        lengths.fill(0); present.fill(false); dirty.fill(false)
        historyLengths.fill(0); historyDirty.fill(false)
        tyreSetLengths.fill(0); tyreSetDirty.fill(false)
    }

    /** Copies unless the payload after the header is identical (the header changes every packet). */
    private fun copyIfChanged(source: ByteBuffer, length: Int, target: ByteBuffer, previousLength: Int): Boolean {
        val src = source.array()
        val off = source.arrayOffset()
        val dst = target.array()
        if (length == previousLength) {
            var same = true
            for (i in HeaderLayout.SIZE until length) {
                if (src[off + i] != dst[i]) {
                    same = false
                    break
                }
            }
            if (same) return false
        }
        System.arraycopy(src, off, dst, 0, length)
        return true
    }

    private fun perCar(size: Int) =
        Array(PacketFormat.MAX_CARS) { ByteBuffer.wrap(ByteArray(size)).order(ByteOrder.LITTLE_ENDIAN) }

    private companion object {
        const val HISTORY_SIZE = 1460
        const val TYRE_SETS_SIZE = 231
    }
}
