package com.dashwroom.f1telemetry.replay.record

import java.io.BufferedInputStream
import java.io.Closeable
import java.io.DataInputStream
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.IOException

/** Sequential reader for `.bin` captures. */
class RecordingReader(file: File) : Closeable {
    private val input = DataInputStream(BufferedInputStream(FileInputStream(file), 64 * 1024))
    private val scratch = ByteArray(8)

    val wallClockStartMillis: Long

    /** Receive time of the record returned by the last [next] call, relative to the recording start. */
    var timestampNanos = 0L; private set

    init {
        val magic = ByteArray(4)
        input.readFully(magic)
        if (!magic.contentEquals(RecordingFormat.MAGIC)) {
            input.close()
            throw IOException("Not a Dashwroom recording (bad magic)")
        }
        val version = readU16()
        if (version != RecordingFormat.VERSION) {
            input.close()
            throw IOException("Unsupported recording version $version")
        }
        readU16()
        wallClockStartMillis = readI64()
    }

    /** Reads the next datagram into [into]; returns its length, or -1 at end of file. */
    fun next(into: ByteArray): Int {
        val ts = try {
            readI64()
        } catch (_: EOFException) {
            return -1
        }
        val length = readU16()
        if (length > into.size) throw IOException("Record of $length bytes exceeds buffer")
        try {
            input.readFully(into, 0, length)
        } catch (_: EOFException) {
            return -1 // truncated final record (e.g. app killed mid-write)
        }
        timestampNanos = ts
        return length
    }

    override fun close() = input.close()

    private fun readU16(): Int {
        input.readFully(scratch, 0, 2)
        return (scratch[0].toInt() and 0xFF) or ((scratch[1].toInt() and 0xFF) shl 8)
    }

    private fun readI64(): Long {
        input.readFully(scratch, 0, 8)
        var v = 0L
        for (i in 7 downTo 0) v = (v shl 8) or (scratch[i].toLong() and 0xFF)
        return v
    }
}
