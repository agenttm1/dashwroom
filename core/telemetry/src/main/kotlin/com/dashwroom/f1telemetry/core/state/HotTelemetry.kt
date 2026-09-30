package com.dashwroom.f1telemetry.core.state

import com.dashwroom.f1telemetry.core.protocol.PacketFormat
import java.util.concurrent.atomic.AtomicLongArray

/**
 * Latest high-frequency values, written by the ingest thread and read by the UI once per display
 * frame (inside `withFrameNanos` / draw lambdas). Never queues, never blocks, never allocates.
 *
 * Every field is individually volatile, so a reader always sees the newest value of each field;
 * different fields may come from adjacent packets, which is invisible at display rates. [version]
 * increases after every update so a frame loop can skip redraws when nothing changed.
 *
 * Car positions are packed (x, z) float pairs in one long so each car's point is read atomically.
 * The previous and current motion samples are both kept, which lets a 90/120 Hz display
 * interpolate between 60 Hz packets.
 */
class HotTelemetry {
    @Volatile var version = 0L; private set

    /** System.nanoTime() when the newest hot packet was received; 0 before any data. */
    @Volatile var lastUpdateNanos = 0L; private set
    @Volatile var sessionTime = 0f
    @Volatile var playerCarIndex = 0

    // Player car — Car Telemetry (packet 6).
    @Volatile var speedKph = 0
    @Volatile var throttle = 0f
    @Volatile var brake = 0f
    @Volatile var steer = 0f
    @Volatile var clutch = 0
    @Volatile var gear = 0
    @Volatile var engineRpm = 0
    @Volatile var drsOpen = false
    @Volatile var revLightsPercent = 0
    @Volatile var revLightsBitValue = 0
    @Volatile var suggestedGear = 0

    // Player car — Car Status (packet 7).
    @Volatile var maxRpm = 0
    @Volatile var idleRpm = 0
    @Volatile var maxGears = 0
    @Volatile var drsAllowed = false
    @Volatile var drsActivationDistance = 0
    @Volatile var ersStoreEnergy = 0f
    @Volatile var ersDeployMode = 0
    @Volatile var fuelInTank = 0f
    @Volatile var fuelRemainingLaps = 0f

    // Player car — Lap Data (packet 2).
    @Volatile var currentLapTimeMs = 0L
    @Volatile var lastLapTimeMs = 0L
    @Volatile var currentLapNum = 0
    @Volatile var carPosition = 0
    @Volatile var sector = 0
    @Volatile var currentLapInvalid = false
    @Volatile var lapDistance = 0f

    // Player live deltas (ms) at the same track position; Int.MIN_VALUE = no reference yet.
    @Volatile var deltaToPersonalBestMs = NO_DELTA
    @Volatile var deltaToSessionBestMs = NO_DELTA
    @Volatile var deltaToLastLapMs = NO_DELTA

    // 2026: Overtake Mode / active aero for the player.
    @Volatile var overtakeAvailable = false
    @Volatile var overtakeActive = false
    @Volatile var activeAeroStraightMode = false

    // All cars — Motion (packet 0) + which slots are live (Lap Data result status).
    private val positionCurrent = AtomicLongArray(PacketFormat.MAX_CARS)
    private val positionPrevious = AtomicLongArray(PacketFormat.MAX_CARS)
    @Volatile var motionCurrentNanos = 0L; private set
    @Volatile var motionPreviousNanos = 0L; private set

    /** Bit i set = vehicle index i is active (result status not invalid/inactive). */
    @Volatile var activeCarsMask = 0

    fun carX(index: Int): Float = Float.fromBits((positionCurrent.get(index) ushr 32).toInt())

    fun carZ(index: Int): Float = Float.fromBits(positionCurrent.get(index).toInt())

    fun previousCarX(index: Int): Float = Float.fromBits((positionPrevious.get(index) ushr 32).toInt())

    fun previousCarZ(index: Int): Float = Float.fromBits(positionPrevious.get(index).toInt())

    fun hasDelta(value: Int): Boolean = value != NO_DELTA

    fun isCarActive(index: Int): Boolean = (activeCarsMask ushr index) and 1 == 1

    // ---- writer side (ingest thread only) ----

    internal fun beginMotionFrame(receivedAtNanos: Long) {
        for (i in 0 until PacketFormat.MAX_CARS) positionPrevious.lazySet(i, positionCurrent.get(i))
        motionPreviousNanos = motionCurrentNanos
        motionCurrentNanos = receivedAtNanos
    }

    internal fun setCarPosition(index: Int, x: Float, z: Float) {
        positionCurrent.lazySet(index, (x.toRawBits().toLong() shl 32) or (z.toRawBits().toLong() and 0xFFFF_FFFFL))
    }

    /** Publishes everything written since the previous call. */
    internal fun publish(receivedAtNanos: Long) {
        lastUpdateNanos = receivedAtNanos
        version = version + 1
    }

    internal fun reset() {
        speedKph = 0; throttle = 0f; brake = 0f; steer = 0f; clutch = 0; gear = 0; engineRpm = 0
        drsOpen = false; revLightsPercent = 0; revLightsBitValue = 0; suggestedGear = 0
        maxRpm = 0; idleRpm = 0; maxGears = 0; drsAllowed = false; drsActivationDistance = 0
        ersStoreEnergy = 0f; ersDeployMode = 0; fuelInTank = 0f; fuelRemainingLaps = 0f
        currentLapTimeMs = 0; lastLapTimeMs = 0; currentLapNum = 0; carPosition = 0; sector = 0
        currentLapInvalid = false; lapDistance = 0f; activeCarsMask = 0; sessionTime = 0f
        deltaToPersonalBestMs = NO_DELTA; deltaToSessionBestMs = NO_DELTA; deltaToLastLapMs = NO_DELTA
        overtakeAvailable = false; overtakeActive = false; activeAeroStraightMode = false
        for (i in 0 until PacketFormat.MAX_CARS) {
            positionCurrent.set(i, 0)
            positionPrevious.set(i, 0)
        }
        motionCurrentNanos = 0; motionPreviousNanos = 0; lastUpdateNanos = 0
        version = version + 1
    }

    companion object {
        const val NO_DELTA = Int.MIN_VALUE
    }
}
