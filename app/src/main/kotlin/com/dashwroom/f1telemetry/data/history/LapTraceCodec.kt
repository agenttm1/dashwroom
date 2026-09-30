package com.dashwroom.f1telemetry.data.history

import com.dashwroom.f1telemetry.core.model.LapTrace
import com.dashwroom.f1telemetry.core.model.SectorTimes
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * Serialises a [LapTrace]'s channels for the database: a small header (magic, version, bin count)
 * followed by the seven float channels, little-endian, deflate-compressed.
 */
object LapTraceCodec {
    private const val MAGIC = 0x4C545231 // "LTR1"
    private const val VERSION = 1
    private const val CHANNELS = 7

    fun encode(trace: LapTrace): ByteArray {
        val n = trace.bins
        val raw = ByteBuffer.allocate(12 + CHANNELS * n * 4).order(ByteOrder.LITTLE_ENDIAN)
        raw.putInt(MAGIC).putInt(VERSION).putInt(n)
        for (ch in channels(trace)) for (v in ch) raw.putFloat(v)
        val deflater = Deflater(Deflater.BEST_SPEED)
        try {
            deflater.setInput(raw.array())
            deflater.finish()
            val out = ByteArrayOutputStream(raw.capacity() / 3)
            val buf = ByteArray(16 * 1024)
            while (!deflater.finished()) out.write(buf, 0, deflater.deflate(buf))
            return out.toByteArray()
        } finally {
            deflater.end()
        }
    }

    fun decode(entity: LapEntity): LapTrace {
        val inflater = Inflater()
        val raw: ByteArray
        try {
            inflater.setInput(entity.traces)
            val out = ByteArrayOutputStream(entity.traces.size * 3)
            val buf = ByteArray(16 * 1024)
            while (!inflater.finished()) {
                val count = inflater.inflate(buf)
                if (count == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
                out.write(buf, 0, count)
            }
            raw = out.toByteArray()
        } finally {
            inflater.end()
        }
        val bb = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
        require(bb.remaining() >= 12 && bb.getInt() == MAGIC) { "Not a lap trace" }
        require(bb.getInt() == VERSION) { "Unsupported lap trace version" }
        val n = bb.getInt()
        require(n in 1..65_536 && bb.remaining() >= CHANNELS * n * 4) { "Truncated lap trace" }
        val ch = Array(CHANNELS) { FloatArray(n) { bb.getFloat() } }
        return LapTrace(
            sessionUid = entity.sessionUid,
            lapNumber = entity.lapNumber,
            lapTimeMs = entity.lapTimeMs,
            sectorsMs = SectorTimes(entity.s1Ms, entity.s2Ms, entity.s3Ms),
            valid = entity.valid,
            trackLengthM = entity.trackLengthM,
            tyreVisual = entity.tyreVisual,
            speedKph = ch[0], throttle = ch[1], brake = ch[2], gear = ch[3], steer = ch[4], rpm = ch[5], time = ch[6],
        )
    }

    private fun channels(t: LapTrace) = arrayOf(t.speedKph, t.throttle, t.brake, t.gear, t.steer, t.rpm, t.time)
}
