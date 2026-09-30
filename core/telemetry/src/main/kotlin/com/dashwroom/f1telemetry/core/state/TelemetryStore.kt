package com.dashwroom.f1telemetry.core.state

import com.dashwroom.f1telemetry.core.model.HistoryState
import com.dashwroom.f1telemetry.core.model.LapTrace
import com.dashwroom.f1telemetry.core.model.PlayerCarState
import com.dashwroom.f1telemetry.core.model.RaceEvent
import com.dashwroom.f1telemetry.core.model.RaceState
import com.dashwroom.f1telemetry.core.model.SessionState
import com.dashwroom.f1telemetry.core.model.TrackOutline
import com.dashwroom.f1telemetry.core.packet.CarDamagePacket
import com.dashwroom.f1telemetry.core.packet.CarStatusPacket
import com.dashwroom.f1telemetry.core.packet.CarTelemetry2Packet
import com.dashwroom.f1telemetry.core.packet.CarTelemetryPacket
import com.dashwroom.f1telemetry.core.packet.EventPacket
import com.dashwroom.f1telemetry.core.packet.F1Packet
import com.dashwroom.f1telemetry.core.packet.FinalClassificationPacket
import com.dashwroom.f1telemetry.core.packet.LapDataPacket
import com.dashwroom.f1telemetry.core.packet.LapPositionsPacket
import com.dashwroom.f1telemetry.core.packet.MotionExPacket
import com.dashwroom.f1telemetry.core.packet.MotionPacket
import com.dashwroom.f1telemetry.core.packet.ParticipantsPacket
import com.dashwroom.f1telemetry.core.packet.SessionHistoryPacket
import com.dashwroom.f1telemetry.core.packet.SessionPacket
import com.dashwroom.f1telemetry.core.packet.TyreSetsPacket
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.nio.ByteBuffer

/**
 * Merges decoded packets into application state. Ingest thread only.
 *
 * Per packet (60 Hz, allocation-free): update [hot] values, per-car live arrays, lap-timing
 * traces, the lap recorder and the track outline, and stash the raw bytes of "cold" packets.
 * At most 10×/s (immediately for session/participants/final classification) the stash is decoded
 * into immutable models: [session], [race], [player], [history]. Events publish as they happen.
 */
class TelemetryStore(val hot: HotTelemetry) {
    private val cars = CarsLive()
    private val roster = Roster()
    private val stash = ColdStash()
    private val traces = LapTimingTraces(cars, hot)
    private val builder = ColdModelBuilder(stash, roster, cars, traces)
    private val outlineBuilder = TrackOutlineBuilder(cars)
    private val recorder = PlayerLapRecorder(cars)
    private val eventLog = EventLog(roster, cars)

    val session: StateFlow<SessionState> = builder.session
    val race: StateFlow<RaceState> = builder.race
    val player: StateFlow<PlayerCarState> = builder.player
    val history: StateFlow<HistoryState> = builder.history
    val events: StateFlow<ImmutableList<RaceEvent>> = eventLog.events
    val trackOutline: StateFlow<TrackOutline?> = outlineBuilder.outline
    val laps: StateFlow<ImmutableList<LapTrace>> = recorder.laps
    val completedLaps: SharedFlow<LapTrace> = recorder.completed

    private var sessionUid = 0L
    private var hasSession = false

    fun apply(packet: F1Packet, buffer: ByteBuffer, length: Int, receivedAtNanos: Long) {
        val header = packet.header
        if (!hasSession || header.sessionUid != sessionUid) startNewSession(packet)
        val player = header.playerCarIndex
        hot.playerCarIndex = player
        hot.sessionTime = header.sessionTime
        var force = false
        when (packet) {
            is CarTelemetryPacket -> {
                applyTelemetry(packet)
                recorder.applyTelemetry(packet)
                stash.stash(packet.packetId, 0, buffer, length)
            }
            is CarStatusPacket -> {
                applyStatus(packet)
                recorder.applyStatus(packet)
                stash.stash(packet.packetId, 0, buffer, length)
            }
            is LapDataPacket -> {
                cars.update(packet)
                applyLapData(packet)
                traces.apply(packet)
                recorder.applyLapData(packet)
                stash.stash(packet.packetId, 0, buffer, length)
            }
            is MotionPacket -> {
                applyMotion(packet, receivedAtNanos)
                outlineBuilder.apply(packet, receivedAtNanos)
            }
            is SessionPacket -> {
                cars.trackLengthM = packet.trackLength.toFloat()
                stash.stash(packet.packetId, 0, buffer, length)
                force = true
            }
            is ParticipantsPacket -> {
                roster.update(packet)
                stash.stash(packet.packetId, 0, buffer, length)
                force = true
            }
            is EventPacket -> eventLog.apply(packet, player)
            is CarDamagePacket, is MotionExPacket, is LapPositionsPacket ->
                stash.stash(packet.packetId, 0, buffer, length)
            is SessionHistoryPacket -> stash.stash(packet.packetId, packet.carIdx, buffer, length)
            is TyreSetsPacket -> stash.stash(packet.packetId, packet.carIdx, buffer, length)
            is FinalClassificationPacket -> {
                stash.stash(packet.packetId, 0, buffer, length)
                force = true
            }
            is CarTelemetry2Packet -> {
                if (player < packet.numCars) {
                    val c = packet.cars[player]
                    hot.overtakeAvailable = c.overtakeAvailable
                    hot.overtakeActive = c.overtakeActive
                    hot.activeAeroStraightMode = c.activeAeroMode == 1
                }
                stash.stash(packet.packetId, 0, buffer, length)
            }
        }
        hot.publish(receivedAtNanos)
        builder.maybePublish(receivedAtNanos, force)
    }

    fun reset() {
        hasSession = false
        sessionUid = 0L
        resetAll()
        builder.clear()
    }

    private fun startNewSession(packet: F1Packet) {
        hasSession = true
        sessionUid = packet.header.sessionUid
        resetAll()
        builder.reset(sessionUid, packet.format, packet.header.playerCarIndex)
    }

    private fun resetAll() {
        hot.reset()
        cars.reset()
        roster.reset()
        stash.clear()
        traces.reset()
        outlineBuilder.reset()
        recorder.reset()
        eventLog.reset()
    }

    private fun applyTelemetry(p: CarTelemetryPacket) {
        val i = p.header.playerCarIndex
        if (i >= p.numCars) return
        val c = p.cars[i]
        hot.speedKph = c.speedKph
        hot.throttle = c.throttle
        hot.brake = c.brake
        hot.steer = c.steer
        hot.clutch = c.clutch
        hot.gear = c.gear
        hot.engineRpm = c.engineRpm
        hot.drsOpen = c.drs
        hot.revLightsPercent = c.revLightsPercent
        hot.revLightsBitValue = c.revLightsBitValue
        hot.suggestedGear = p.suggestedGear
    }

    private fun applyStatus(p: CarStatusPacket) {
        val i = p.header.playerCarIndex
        if (i >= p.numCars) return
        val c = p.cars[i]
        hot.maxRpm = c.maxRpm
        hot.idleRpm = c.idleRpm
        hot.maxGears = c.maxGears
        hot.drsAllowed = c.drsAllowed
        hot.drsActivationDistance = c.drsActivationDistance
        hot.ersStoreEnergy = c.ersStoreEnergy
        hot.ersDeployMode = c.ersDeployMode
        hot.fuelInTank = c.fuelInTank
        hot.fuelRemainingLaps = c.fuelRemainingLaps
    }

    private fun applyLapData(p: LapDataPacket) {
        var mask = 0
        for (i in 0 until p.numCars) {
            if (p.cars[i].resultStatus >= RESULT_STATUS_ACTIVE) mask = mask or (1 shl i)
        }
        hot.activeCarsMask = mask
        val i = p.header.playerCarIndex
        if (i >= p.numCars) return
        val c = p.cars[i]
        hot.currentLapTimeMs = c.currentLapTimeMs
        hot.lastLapTimeMs = c.lastLapTimeMs
        hot.currentLapNum = c.currentLapNum
        hot.carPosition = c.carPosition
        hot.sector = c.sector
        hot.currentLapInvalid = c.currentLapInvalid
        hot.lapDistance = c.lapDistance
    }

    private fun applyMotion(p: MotionPacket, receivedAtNanos: Long) {
        hot.beginMotionFrame(receivedAtNanos)
        for (i in 0 until p.numCars) {
            val c = p.cars[i]
            hot.setCarPosition(i, c.worldPositionX, c.worldPositionZ)
        }
    }

    private companion object {
        const val RESULT_STATUS_ACTIVE = 2
    }
}
