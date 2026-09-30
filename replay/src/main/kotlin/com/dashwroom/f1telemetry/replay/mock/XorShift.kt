package com.dashwroom.f1telemetry.replay.mock

/** Tiny deterministic PRNG: no atomics, no allocation, reproducible mock sessions. */
class XorShift(seed: Long) {
    private var state = if (seed == 0L) 0x9E3779B97F4A7C15uL.toLong() else seed

    fun nextLong(): Long {
        var x = state
        x = x xor (x shl 13)
        x = x xor (x ushr 7)
        x = x xor (x shl 17)
        state = x
        return x
    }

    /** Uniform in [0, 1). */
    fun nextFloat(): Float = ((nextLong() ushr 40).toInt()) / (1 shl 24).toFloat()

    fun nextInt(bound: Int): Int = ((nextLong() ushr 1) % bound).toInt()

    fun range(from: Float, to: Float): Float = from + (to - from) * nextFloat()
}
