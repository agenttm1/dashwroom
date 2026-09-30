package com.dashwroom.f1telemetry.core.state

import com.dashwroom.f1telemetry.core.model.LapTrace
import com.dashwroom.f1telemetry.core.model.SectorTimes
import com.dashwroom.f1telemetry.core.packet.CarStatusPacket
import com.dashwroom.f1telemetry.core.packet.CarTelemetryPacket
import com.dashwroom.f1telemetry.core.packet.LapDataPacket
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Records the player's speed / throttle / brake / gear / steering / RPM against track distance for
 * the lap in progress, and publishes an immutable [LapTrace] when the lap completes. Sampling
 * writes into pre-allocated arrays; the only allocation is the snapshot once per lap.
 */
internal class PlayerLapRecorder(private val cars: CarsLive) {
    private val speed = FloatArray(BINS)
    private val throttle = FloatArray(BINS)
    private val brake = FloatArray(BINS)
    private val gear = FloatArray(BINS)
    private val steer = FloatArray(BINS)
    private val rpm = FloatArray(BINS)
    private val time = FloatArray(BINS)
    private val written = BooleanArray(BINS)
    private var lastBin = -1
    private var lap = 0
    private var lapInvalid = false
    private var sector1Ms = 0
    private var sector2Ms = 0
    private var tyreVisual = 0
    private var sessionUid = 0L

    private val _laps = MutableStateFlow<ImmutableList<LapTrace>>(persistentListOf())
    val laps: StateFlow<ImmutableList<LapTrace>> = _laps.asStateFlow()

    private val _completed = MutableSharedFlow<LapTrace>(extraBufferCapacity = 16)
    val completed: SharedFlow<LapTrace> = _completed.asSharedFlow()

    fun applyLapData(p: LapDataPacket) {
        val i = p.header.playerCarIndex
        if (i >= p.numCars) return
        val c = p.cars[i]
        sessionUid = p.header.sessionUid
        if (c.currentLapNum != lap) {
            if (c.currentLapNum == lap + 1 && lap > 0) finishLap(c.lastLapTimeMs)
            startLap(c.currentLapNum)
        }
        if (c.currentLapInvalid) lapInvalid = true
        if (c.sector1TimeMs > 0) sector1Ms = c.sector1TimeMs
        if (c.sector2TimeMs > 0) sector2Ms = c.sector2TimeMs
    }

    fun applyStatus(p: CarStatusPacket) {
        val i = p.header.playerCarIndex
        if (i < p.numCars) tyreVisual = p.cars[i].visualTyreCompound
    }

    fun applyTelemetry(p: CarTelemetryPacket) {
        val i = p.header.playerCarIndex
        val length = cars.trackLengthM
        if (i >= p.numCars || length <= 0f || lap == 0) return
        val d = cars.lapDistance[i]
        if (d < 0f || d >= length) return
        val bin = (d / length * BINS).toInt().coerceIn(0, BINS - 1)
        val c = p.cars[i]
        val t = cars.currentLapTimeMs[i] / 1000f
        val from = if (lastBin in 0 until bin && bin - lastBin < MAX_GAP_BINS) lastBin + 1 else bin
        for (b in from..bin) {
            speed[b] = c.speedKph.toFloat()
            throttle[b] = c.throttle
            brake[b] = c.brake
            gear[b] = c.gear.toFloat()
            steer[b] = c.steer
            rpm[b] = c.engineRpm.toFloat()
            time[b] = t
            written[b] = true
        }
        lastBin = bin
    }

    private fun startLap(newLap: Int) {
        lap = newLap
        lastBin = -1
        lapInvalid = false
        sector1Ms = 0
        sector2Ms = 0
        written.fill(false)
    }

    private fun finishLap(lapTimeMs: Long) {
        var count = 0
        for (w in written) if (w) count++
        if (lapTimeMs <= 0 || count < BINS * MIN_COVERAGE) return
        val s3 = (lapTimeMs - sector1Ms - sector2Ms).toInt().coerceAtLeast(0)
        val trace = LapTrace(
            sessionUid = sessionUid,
            lapNumber = lap,
            lapTimeMs = lapTimeMs,
            sectorsMs = SectorTimes(sector1Ms, sector2Ms, s3),
            valid = !lapInvalid,
            trackLengthM = cars.trackLengthM,
            tyreVisual = tyreVisual,
            speedKph = speed.copyOf(),
            throttle = throttle.copyOf(),
            brake = brake.copyOf(),
            gear = gear.copyOf(),
            steer = steer.copyOf(),
            rpm = rpm.copyOf(),
            time = time.copyOf(),
        )
        _laps.value = (_laps.value + trace).takeLast(MAX_LAPS).toPersistentList()
        _completed.tryEmit(trace)
    }

    fun reset() {
        lap = 0
        lastBin = -1
        written.fill(false)
        _laps.value = persistentListOf()
    }

    companion object {
        const val BINS = 2048
        private const val MAX_LAPS = 120
        private const val MAX_GAP_BINS = 64
        private const val MIN_COVERAGE = 0.9f
    }
}
