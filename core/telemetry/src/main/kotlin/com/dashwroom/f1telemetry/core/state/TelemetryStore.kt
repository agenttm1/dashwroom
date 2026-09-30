package com.dashwroom.f1telemetry.core.state

import com.dashwroom.f1telemetry.core.model.ParticipantInfo
import com.dashwroom.f1telemetry.core.model.SessionInfo
import com.dashwroom.f1telemetry.core.model.SessionState
import com.dashwroom.f1telemetry.core.packet.CarStatusPacket
import com.dashwroom.f1telemetry.core.packet.CarTelemetryPacket
import com.dashwroom.f1telemetry.core.packet.EventPacket
import com.dashwroom.f1telemetry.core.packet.F1Packet
import com.dashwroom.f1telemetry.core.packet.LapDataPacket
import com.dashwroom.f1telemetry.core.packet.MotionPacket
import com.dashwroom.f1telemetry.core.packet.ParticipantsPacket
import com.dashwroom.f1telemetry.core.packet.SessionPacket
import com.dashwroom.f1telemetry.core.spec.Appendix
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Merges decoded packets into application state. Runs on the ingest thread only.
 *
 * - Every 60 Hz packet writes straight into [hot]; this path allocates nothing.
 * - Slow packets (session 2 Hz, participants every 5 s) rebuild the immutable [session] model;
 *   [MutableStateFlow] drops the update if nothing actually changed.
 */
class TelemetryStore(val hot: HotTelemetry) {
    private val _session = MutableStateFlow(SessionState.Empty)
    val session: StateFlow<SessionState> = _session.asStateFlow()

    private var sessionUid = 0L

    fun apply(packet: F1Packet, receivedAtNanos: Long) {
        val header = packet.header
        if (header.sessionUid != sessionUid) startNewSession(header.sessionUid, packet)
        hot.playerCarIndex = header.playerCarIndex
        hot.sessionTime = header.sessionTime
        when (packet) {
            is CarTelemetryPacket -> applyTelemetry(packet)
            is CarStatusPacket -> applyStatus(packet)
            is LapDataPacket -> applyLapData(packet)
            is MotionPacket -> applyMotion(packet, receivedAtNanos)
            is SessionPacket -> applySession(packet)
            is ParticipantsPacket -> applyParticipants(packet)
            is EventPacket -> Unit // Event feed arrives in Phase 2.
        }
        hot.publish(receivedAtNanos)
    }

    fun reset() {
        sessionUid = 0L
        hot.reset()
        _session.value = SessionState.Empty
    }

    private fun startNewSession(uid: Long, packet: F1Packet) {
        sessionUid = uid
        hot.reset()
        _session.value = SessionState(
            sessionUid = uid,
            format = packet.format,
            playerCarIndex = packet.header.playerCarIndex,
        )
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

    private fun applySession(p: SessionPacket) {
        val info = SessionInfo(
            trackId = p.trackId,
            trackName = Appendix.trackName(p.trackId),
            trackLengthM = p.trackLength,
            sessionType = p.sessionType,
            sessionTypeName = Appendix.sessionTypeName(p.sessionType),
            weather = p.weather,
            weatherName = Appendix.weatherName(p.weather),
            trackTemperatureC = p.trackTemperature,
            airTemperatureC = p.airTemperature,
            totalLaps = p.totalLaps,
            sessionTimeLeftS = p.sessionTimeLeft,
            sessionDurationS = p.sessionDuration,
            safetyCarStatus = p.safetyCarStatus,
            formula = p.formula,
            pitSpeedLimitKph = p.pitSpeedLimit,
            gamePaused = p.gamePaused,
            networkGame = p.networkGame,
        )
        val current = _session.value
        if (current.info != info || current.format != p.format) {
            _session.value = current.copy(info = info, format = p.format, playerCarIndex = p.header.playerCarIndex)
        }
    }

    private fun applyParticipants(p: ParticipantsPacket) {
        val count = p.numActiveCars.coerceAtMost(p.numCars)
        val list = ArrayList<ParticipantInfo>(count)
        for (i in 0 until count) {
            val c = p.cars[i]
            list += ParticipantInfo(
                vehicleIndex = i,
                name = c.name,
                driverId = c.driverId,
                teamId = c.teamId,
                teamName = Appendix.teamName(c.teamId),
                raceNumber = c.raceNumber,
                nationality = c.nationality,
                aiControlled = c.aiControlled,
                telemetryPublic = c.yourTelemetryPublic,
                liveryColour = if (c.numColours > 0) c.liveryColours[0] else null,
            )
        }
        val current = _session.value
        if (current.participants != list || current.numActiveCars != p.numActiveCars) {
            _session.value = current.copy(
                participants = list.toImmutableList(),
                numActiveCars = p.numActiveCars,
                playerCarIndex = p.header.playerCarIndex,
            )
        }
    }

    private companion object {
        const val RESULT_STATUS_ACTIVE = 2
    }
}
