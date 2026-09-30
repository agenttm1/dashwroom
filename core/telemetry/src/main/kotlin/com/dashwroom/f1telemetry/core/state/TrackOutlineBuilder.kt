package com.dashwroom.f1telemetry.core.state

import com.dashwroom.f1telemetry.core.model.TrackOutline
import com.dashwroom.f1telemetry.core.packet.MotionPacket
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Learns the circuit shape from where every car drives: each Motion packet drops each running
 * car's (x, z) into the bin for its current lap distance. With ~20 cars spread around the lap the
 * outline is complete within seconds. Allocation-free except when publishing a snapshot (≤1 Hz,
 * and only while the outline is still changing).
 */
internal class TrackOutlineBuilder(private val cars: CarsLive) {
    private val x = FloatArray(BINS)
    private val z = FloatArray(BINS)
    private val filled = BooleanArray(BINS)
    private var filledCount = 0
    private var changedSincePublish = false
    private var lastPublishNanos = 0L
    private var version = 0

    private val _outline = MutableStateFlow<TrackOutline?>(null)
    val outline: StateFlow<TrackOutline?> = _outline.asStateFlow()

    fun apply(p: MotionPacket, nowNanos: Long) {
        val length = cars.trackLengthM
        if (length <= 0f) return
        for (i in 0 until p.numCars) {
            if (cars.resultStatus[i] < RESULT_ACTIVE || cars.pitStatus[i] != 0) continue
            val d = cars.lapDistance[i]
            if (d < 0f || d >= length) continue
            val bin = (d / length * BINS).toInt().coerceIn(0, BINS - 1)
            val m = p.cars[i]
            if (m.worldPositionX == 0f && m.worldPositionZ == 0f) continue
            if (!filled[bin]) {
                filled[bin] = true
                filledCount++
                x[bin] = m.worldPositionX
                z[bin] = m.worldPositionZ
                changedSincePublish = true
            } else {
                // Average the racing lines of many cars into a smooth centreline.
                x[bin] += (m.worldPositionX - x[bin]) * SMOOTHING
                z[bin] += (m.worldPositionZ - z[bin]) * SMOOTHING
            }
        }
        if (changedSincePublish && nowNanos - lastPublishNanos > PUBLISH_INTERVAL_NANOS) publish(nowNanos)
    }

    private fun publish(nowNanos: Long) {
        changedSincePublish = false
        lastPublishNanos = nowNanos
        var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
        var minZ = Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE
        for (b in 0 until BINS) if (filled[b]) {
            minX = minOf(minX, x[b]); maxX = maxOf(maxX, x[b])
            minZ = minOf(minZ, z[b]); maxZ = maxOf(maxZ, z[b])
        }
        _outline.value = TrackOutline(
            x.copyOf(), z.copyOf(), filled.copyOf(), filledCount / BINS.toFloat(),
            minX, maxX, minZ, maxZ, ++version,
        )
    }

    fun reset() {
        filled.fill(false)
        filledCount = 0
        changedSincePublish = false
        _outline.value = null
    }

    private companion object {
        const val BINS = 720
        const val SMOOTHING = 0.02f
        const val RESULT_ACTIVE = 2
        const val PUBLISH_INTERVAL_NANOS = 1_000_000_000L
    }
}
