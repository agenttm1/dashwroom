package com.dashwroom.f1telemetry.core.protocol

import java.nio.ByteBuffer

// Little-endian absolute reads. Every buffer handed to the parser is ordered LITTLE_ENDIAN
// (the spec: "all values are encoded using Little Endian format. All data is packed."),
// so these are thin, allocation-free wrappers that just add the unsigned conversions.

internal fun ByteBuffer.u8(index: Int): Int = get(index).toInt() and 0xFF

internal fun ByteBuffer.i8(index: Int): Int = get(index).toInt()

internal fun ByteBuffer.u16(index: Int): Int = getShort(index).toInt() and 0xFFFF

internal fun ByteBuffer.i16(index: Int): Int = getShort(index).toInt()

internal fun ByteBuffer.u32(index: Int): Long = getInt(index).toLong() and 0xFFFF_FFFFL

internal fun ByteBuffer.f32(index: Int): Float = getFloat(index)

internal fun ByteBuffer.f64(index: Int): Double = getDouble(index)

/** uint64 kept in a Long's bit pattern; format with [java.lang.Long.toUnsignedString]. */
internal fun ByteBuffer.u64(index: Int): Long = getLong(index)

internal fun ByteBuffer.flag(index: Int): Boolean = get(index).toInt() != 0
