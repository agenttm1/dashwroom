package com.dashwroom.f1telemetry.replay.mock

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A procedurally generated closed circuit, resampled at uniform arc length, with a physically
 * plausible speed profile (lateral-grip corner limit, then acceleration/braking passes).
 * The app never hardcodes outlines, so any closed curve exercises the auto-scaled track map.
 */
class MockTrack(val lengthM: Float = 5891f, samples: Int = 2048) {
    val count = samples
    val step = lengthM / samples
    val x = FloatArray(samples)
    val z = FloatArray(samples)
    val y = FloatArray(samples)
    val heading = FloatArray(samples)
    val curvature = FloatArray(samples)

    /** Curvature with sign: positive = turning left (heading increasing). */
    val signedCurvature = FloatArray(samples)
    val speed = FloatArray(samples)
    val sector2Start = lengthM / 3f
    val sector3Start = lengthM * 2f / 3f

    /** Straights where DRS is allowed, as (start, end) lap-distance fractions. */
    val drsZones: FloatArray

    init {
        buildGeometry()
        buildSpeedProfile()
        drsZones = findDrsZones()
    }

    /** Index of the sample at or before lap distance [s] (wraps). */
    fun index(s: Float): Int {
        var d = s % lengthM
        if (d < 0) d += lengthM
        return (d / step).toInt().coerceIn(0, count - 1)
    }

    fun speedAt(s: Float): Float = speed[index(s)]

    fun inDrsZone(s: Float): Boolean {
        var d = s % lengthM
        if (d < 0) d += lengthM
        val f = d / lengthM
        var i = 0
        while (i < drsZones.size) {
            if (f >= drsZones[i] && f <= drsZones[i + 1]) return true
            i += 2
        }
        return false
    }

    /** Metres until the next DRS zone starts (0 when inside one). */
    fun distanceToDrsZone(s: Float): Float {
        if (inDrsZone(s)) return 0f
        var d = s % lengthM
        if (d < 0) d += lengthM
        var best = Float.MAX_VALUE
        var i = 0
        while (i < drsZones.size) {
            var gap = drsZones[i] * lengthM - d
            if (gap < 0) gap += lengthM
            if (gap < best) best = gap
            i += 2
        }
        return best
    }

    private fun catmullRom(p0: Double, p1: Double, p2: Double, p3: Double, t: Double): Double {
        val t2 = t * t
        val t3 = t2 * t
        return 0.5 * ((2 * p1) + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3)
    }

    private fun buildGeometry() {
        val n = controlX.size
        val perSegment = 1024
        val dense = n * perSegment
        val px = DoubleArray(dense + 1)
        val pz = DoubleArray(dense + 1)
        val cum = DoubleArray(dense + 1)
        for (k in 0..dense) {
            val seg = (k / perSegment) % n
            val t = (k % perSegment) / perSegment.toDouble()
            val i0 = (seg - 1 + n) % n; val i1 = seg; val i2 = (seg + 1) % n; val i3 = (seg + 2) % n
            px[k] = catmullRom(controlX[i0], controlX[i1], controlX[i2], controlX[i3], t)
            pz[k] = catmullRom(controlZ[i0], controlZ[i1], controlZ[i2], controlZ[i3], t)
            if (k > 0) cum[k] = cum[k - 1] + hypot(px[k] - px[k - 1], pz[k] - pz[k - 1])
        }
        val scale = lengthM / cum[dense]
        var j = 0
        for (i in 0 until count) {
            val target = i * step / scale
            while (j < dense - 1 && cum[j + 1] < target) j++
            val span = cum[j + 1] - cum[j]
            val f = if (span > 0) (target - cum[j]) / span else 0.0
            x[i] = ((px[j] + (px[j + 1] - px[j]) * f) * scale).toFloat()
            z[i] = ((pz[j] + (pz[j + 1] - pz[j]) * f) * scale).toFloat()
            y[i] = (6.0 * sin(2 * PI * i / count * 3)).toFloat()
        }
        for (i in 0 until count) {
            val a = (i - 1 + count) % count
            val b = (i + 1) % count
            heading[i] = atan2(z[b] - z[a], x[b] - x[a])
            curvature[i] = mengerCurvature(x[a], z[a], x[i], z[i], x[b], z[b])
        }
        for (i in 0 until count) {
            var dh = heading[(i + 1) % count] - heading[(i - 1 + count) % count]
            if (dh > PI) dh -= (2 * PI).toFloat()
            if (dh < -PI) dh += (2 * PI).toFloat()
            signedCurvature[i] = dh / (2 * step)
        }
        // Smooth curvature a little so the speed profile isn't jagged.
        val tmp = curvature.copyOf()
        for (i in 0 until count) {
            var sum = 0f
            for (d in -4..4) sum += tmp[(i + d + count) % count]
            curvature[i] = sum / 9f
        }
    }

    private fun mengerCurvature(ax: Float, az: Float, bx: Float, bz: Float, cx: Float, cz: Float): Float {
        val area2 = abs((bx - ax) * (cz - az) - (bz - az) * (cx - ax))
        val ab = hypot(bx - ax, bz - az)
        val bc = hypot(cx - bx, cz - bz)
        val ca = hypot(ax - cx, az - cz)
        val denom = ab * bc * ca
        return if (denom < 1e-6f) 0f else 2f * area2 / denom
    }

    private fun buildSpeedProfile() {
        for (i in 0 until count) {
            val k = curvature[i]
            speed[i] = cornerSpeed(k)
        }
        // Two laps of forward (traction) and backward (braking) passes to handle wrap-around.
        repeat(2) {
            for (n in 1..count) {
                val i = n % count
                val prev = speed[(i - 1 + count) % count]
                val accel = 13f * (1f - prev / V_MAX) + 1.5f
                speed[i] = min(speed[i], sqrt(prev * prev + 2 * accel * step))
            }
            for (n in count - 1 downTo 0) {
                val next = speed[(n + 1) % count]
                speed[n] = min(speed[n], sqrt(next * next + 2 * BRAKING * step))
            }
        }
    }

    /**
     * Grip grows with downforce: a_lat = mu·(g + kd·v²), so v² = mu·g·R / (1 − mu·kd·R).
     * Slow corners are grip-limited, fast ones become flat out.
     */
    private fun cornerSpeed(k: Float): Float {
        if (k < 1e-6f) return V_MAX
        val r = 1f / k
        val denom = 1f - MU * DOWNFORCE * r
        if (denom <= 0f) return V_MAX
        return min(V_MAX, sqrt(MU * 9.81f * r / denom))
    }

    private fun findDrsZones(): FloatArray {
        // The two longest runs at (near) top speed become DRS zones.
        val threshold = V_MAX * 0.97f
        var bestLen = 0; var bestStart = 0
        var secondLen = 0; var secondStart = 0
        var i = 0
        val visited = BooleanArray(count)
        while (i < count) {
            if (speed[i] >= threshold && !visited[i]) {
                var len = 0
                var k = i
                while (speed[k % count] >= threshold && len < count) {
                    visited[k % count] = true; len++; k++
                }
                if (len > bestLen) {
                    secondLen = bestLen; secondStart = bestStart
                    bestLen = len; bestStart = i
                } else if (len > secondLen) {
                    secondLen = len; secondStart = i
                }
                i = k
            } else {
                i++
            }
        }
        val zones = ArrayList<Float>(4)
        if (bestLen > 0) { zones += bestStart / count.toFloat(); zones += (bestStart + bestLen) / count.toFloat() }
        if (secondLen > 0) { zones += secondStart / count.toFloat(); zones += (secondStart + secondLen) / count.toFloat() }
        return FloatArray(zones.size) { zones[it].coerceAtMost(1f) }
    }

    fun idealLapTimeSeconds(): Float {
        var t = 0f
        for (i in 0 until count) t += step / speed[i]
        return t
    }

    fun minSpeed(): Float = speed.min()

    companion object {
        /**
         * Circuit outline control points (arbitrary units): main straight, T1 hairpin, esses,
         * chicane, back straight, final corner. A closed Catmull-Rom spline runs through them.
         */
        private val controlX = doubleArrayOf(
            0.0, 0.55, 1.1, 1.16, 1.185, 1.18, 1.155, 1.0, 0.85, 0.8, 0.84, 0.95, 1.1, 1.15, 1.1, 0.95, 0.9, 0.95, 1.05, 1.08, 0.98, 0.6, 0.3, 0.2, 0.17, 0.2, 0.28, 0.25, 0.12, 0.05, -0.08, -0.12, -0.08, -0.03,
        )
        private val controlZ = doubleArrayOf(
            0.0, 0.0, 0.0, 0.005, 0.025, 0.05, 0.06, 0.1, 0.16, 0.22, 0.28, 0.35, 0.45, 0.55, 0.63, 0.66, 0.7, 0.74, 0.8, 0.92, 1.0, 1.02, 1.0, 0.98, 0.94, 0.9, 0.8, 0.66, 0.58, 0.45, 0.3, 0.15, 0.05, 0.01,
        )

        const val V_MAX = 91f // m/s ≈ 328 km/h
        const val MU = 2.0f
        const val DOWNFORCE = 0.0040f
        const val BRAKING = 42f // m/s²
    }
}
