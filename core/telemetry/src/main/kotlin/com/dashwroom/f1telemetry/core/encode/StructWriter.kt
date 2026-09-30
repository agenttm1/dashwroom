package com.dashwroom.f1telemetry.core.encode

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Sequential little-endian writer. Encoders call it field by field in the order the spec's C
 * structs declare them — deliberately independent of the parsers' absolute offset tables, so a
 * round-trip test catches an offset mistake in either one.
 */
class StructWriter(capacity: Int) {
    val bytes = ByteArray(capacity)
    val buffer: ByteBuffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

    val position: Int get() = buffer.position()

    fun reset(): StructWriter {
        buffer.clear()
        return this
    }

    fun u8(v: Int) { buffer.put(v.toByte()) }
    fun i8(v: Int) { buffer.put(v.toByte()) }
    fun bool(v: Boolean) { buffer.put(if (v) 1 else 0) }
    fun u16(v: Int) { buffer.putShort(v.toShort()) }
    fun i16(v: Int) { buffer.putShort(v.toShort()) }
    fun u32(v: Long) { buffer.putInt(v.toInt()) }
    fun f32(v: Float) { buffer.putFloat(v) }
    fun f64(v: Double) { buffer.putDouble(v) }
    fun u64(v: Long) { buffer.putLong(v) }

    fun zeros(count: Int) {
        repeat(count) { buffer.put(0) }
    }

    /** Writes [src] (up to [length] bytes) into a fixed [fieldSize] field, zero padded. */
    fun fixedBytes(src: ByteArray, length: Int, fieldSize: Int) {
        val n = minOf(length, fieldSize)
        buffer.put(src, 0, n)
        zeros(fieldSize - n)
    }
}
