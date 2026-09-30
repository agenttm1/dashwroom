package com.dashwroom.f1telemetry.replay.record

import com.dashwroom.f1telemetry.core.ingest.DatagramTap
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.locks.LockSupport

/**
 * Records raw datagrams to a `.bin` file without ever blocking the ingest thread.
 *
 * The ingest thread copies each datagram into a pre-allocated single-producer/single-consumer
 * ring; a background writer thread drains the ring to disk. If storage stalls long enough to fill
 * the ring, datagrams are dropped (and counted) rather than slowing ingest down.
 */
class PacketRecorder(private val ringCapacity: Int = 8 * 1024 * 1024) : DatagramTap {
    private val ring = ByteArray(ringCapacity)

    @Volatile private var head = 0L // total bytes produced
    @Volatile private var tail = 0L // total bytes consumed
    @Volatile private var running = false
    private var startNanos = 0L
    private var writer: Thread? = null

    @Volatile var packetsRecorded = 0L; private set
    @Volatile var packetsDropped = 0L; private set
    @Volatile var bytesWritten = 0L; private set

    var file: File? = null; private set

    val isRecording: Boolean get() = running

    /** Starts recording to [target] (overwritten). Call from any thread while not recording. */
    @Synchronized
    fun start(target: File, nowNanos: Long = System.nanoTime(), wallClockMillis: Long = System.currentTimeMillis()) {
        check(!running) { "Already recording" }
        target.parentFile?.mkdirs()
        val out = BufferedOutputStream(FileOutputStream(target), 64 * 1024)
        val header = ByteBuffer.allocate(RecordingFormat.FILE_HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        header.put(RecordingFormat.MAGIC).putShort(RecordingFormat.VERSION.toShort()).putShort(0).putLong(wallClockMillis)
        out.write(header.array())
        head = 0; tail = 0; packetsRecorded = 0; packetsDropped = 0
        bytesWritten = RecordingFormat.FILE_HEADER_SIZE.toLong()
        startNanos = nowNanos
        file = target
        running = true
        writer = Thread({ drainLoop(out) }, "packet-recorder").apply {
            isDaemon = true
            start()
        }
    }

    /** Stops recording, flushes everything captured so far and closes the file. */
    @Synchronized
    fun stop() {
        if (!running) return
        running = false
        writer?.join()
        writer = null
    }

    /** Ingest thread. Never blocks, never allocates. */
    override fun onDatagram(buffer: ByteBuffer, length: Int, receivedAtNanos: Long) {
        if (!running) return
        val needed = RecordingFormat.RECORD_HEADER_SIZE + length
        val h = head
        if (ringCapacity - (h - tail) < needed) {
            packetsDropped++
            return
        }
        var p = putLong(h, receivedAtNanos - startNanos)
        p = putByte(p, length)
        p = putByte(p, length ushr 8)
        val src = buffer.array()
        val offset = buffer.arrayOffset()
        val at = (p % ringCapacity).toInt()
        val first = minOf(length, ringCapacity - at)
        System.arraycopy(src, offset, ring, at, first)
        if (first < length) System.arraycopy(src, offset + first, ring, 0, length - first)
        head = p + length // publish
        packetsRecorded++
    }

    private fun putLong(pos: Long, value: Long): Long {
        var p = pos
        for (shift in 0 until 64 step 8) p = putByte(p, (value ushr shift).toInt())
        return p
    }

    private fun putByte(pos: Long, value: Int): Long {
        ring[(pos % ringCapacity).toInt()] = value.toByte()
        return pos + 1
    }

    private fun drainLoop(out: BufferedOutputStream) {
        out.use {
            while (true) {
                val stillRunning = running
                val h = head
                val t = tail
                if (h > t) {
                    val at = (t % ringCapacity).toInt()
                    val count = (h - t).toInt()
                    val first = minOf(count, ringCapacity - at)
                    it.write(ring, at, first)
                    if (first < count) it.write(ring, 0, count - first)
                    bytesWritten += count
                    tail = h
                } else if (!stillRunning) {
                    break
                } else {
                    it.flush()
                    LockSupport.parkNanos(DRAIN_INTERVAL_NANOS)
                }
            }
            it.flush()
        }
    }

    private companion object {
        const val DRAIN_INTERVAL_NANOS = 20_000_000L
    }
}
