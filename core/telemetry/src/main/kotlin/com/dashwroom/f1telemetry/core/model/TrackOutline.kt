package com.dashwroom.f1telemetry.core.model

/**
 * The circuit shape learned from where cars actually drive: world (x, z) per lap-distance bin.
 * Nothing is hardcoded per track, so any circuit (and reverse layouts) works. Treat the arrays
 * as read-only; a new instance is published whenever the outline improves.
 */
class TrackOutline(
    val x: FloatArray,
    val z: FloatArray,
    val filled: BooleanArray,
    val coverage: Float,
    val minX: Float,
    val maxX: Float,
    val minZ: Float,
    val maxZ: Float,
    val version: Int,
) {
    val bins: Int get() = x.size

    override fun equals(other: Any?): Boolean = other is TrackOutline && other.version == version

    override fun hashCode(): Int = version
}
