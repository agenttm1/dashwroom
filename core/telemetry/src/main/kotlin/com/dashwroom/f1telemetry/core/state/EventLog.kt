package com.dashwroom.f1telemetry.core.state

import com.dashwroom.f1telemetry.core.model.RaceEvent
import com.dashwroom.f1telemetry.core.model.RaceEventType
import com.dashwroom.f1telemetry.core.packet.EventCode
import com.dashwroom.f1telemetry.core.packet.EventPacket
import com.dashwroom.f1telemetry.core.spec.Appendix
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Turns Event packets into readable feed lines (newest first). Events are rare, so building an
 * immutable entry per event is fine; button-status spam (BUTN) is ignored entirely.
 */
internal class EventLog(private val roster: Roster, private val cars: CarsLive) {
    private val _events = MutableStateFlow<ImmutableList<RaceEvent>>(persistentListOf())
    val events: StateFlow<ImmutableList<RaceEvent>> = _events.asStateFlow()
    private var nextId = 1L

    fun apply(e: EventPacket, playerIndex: Int) {
        val code = e.code
        if (code == EventCode.BUTTON_STATUS) return
        val lap = cars.lapNum.getOrElse(playerIndex) { 0 }
        fun n(i: Int) = if (i in 0 until roster.names.size) roster.codes[i] else "#$i"
        val (type, text, a, b) = when (code) {
            EventCode.SESSION_STARTED -> Quad(RaceEventType.SESSION_STARTED, "Session started")
            EventCode.SESSION_ENDED -> Quad(RaceEventType.SESSION_ENDED, "Session ended")
            EventCode.FASTEST_LAP -> Quad(RaceEventType.FASTEST_LAP, "Fastest lap: ${n(e.vehicleIdx)} ${lapTime(e.lapTimeSeconds)}", e.vehicleIdx)
            EventCode.RETIREMENT -> Quad(RaceEventType.RETIREMENT, "${n(e.vehicleIdx)} retired — ${retirementReason(e.reason)}", e.vehicleIdx)
            EventCode.DRS_ENABLED -> Quad(RaceEventType.DRS_ENABLED, "DRS enabled")
            EventCode.DRS_DISABLED -> Quad(RaceEventType.DRS_DISABLED, "DRS disabled — ${drsReason(e.reason)}")
            EventCode.TEAM_MATE_IN_PITS -> Quad(RaceEventType.TEAM_MATE_IN_PITS, "Team mate ${n(e.vehicleIdx)} in the pits", e.vehicleIdx)
            EventCode.CHEQUERED_FLAG -> Quad(RaceEventType.CHEQUERED_FLAG, "Chequered flag")
            EventCode.RACE_WINNER -> Quad(RaceEventType.RACE_WINNER, "${roster.names.getOrElse(e.vehicleIdx) { "?" }} wins", e.vehicleIdx)
            EventCode.PENALTY -> with(e.penalty) {
                val kind = Appendix.penaltyTypes.getOrElse(penaltyType) { "Penalty" }
                val why = Appendix.infringementTypes.getOrElse(infringementType) { "" }
                val secs = if (penaltyType == PENALTY_TIME && timeSeconds in 1..254) " +${timeSeconds}s" else ""
                Quad(RaceEventType.PENALTY, "${n(vehicleIdx)}: $kind$secs — $why", vehicleIdx, otherVehicleIdx)
            }
            EventCode.SPEED_TRAP -> with(e.speedTrap) {
                if (!isOverallFastestInSession && vehicleIdx != playerIndex) return
                Quad(RaceEventType.SPEED_TRAP, "Speed trap: ${n(vehicleIdx)} ${speedKph.toInt()} km/h", vehicleIdx)
            }
            EventCode.START_LIGHTS -> Quad(RaceEventType.START_LIGHTS, "Start lights: ${e.numLights}")
            EventCode.LIGHTS_OUT -> Quad(RaceEventType.LIGHTS_OUT, "Lights out — away we go")
            EventCode.DRIVE_THROUGH_SERVED -> Quad(RaceEventType.DRIVE_THROUGH_SERVED, "${n(e.vehicleIdx)} served drive-through", e.vehicleIdx)
            EventCode.STOP_GO_SERVED -> Quad(RaceEventType.STOP_GO_SERVED, "${n(e.vehicleIdx)} served stop-go (${String.format(Locale.US, "%.1f", e.stopTimeSeconds)}s)", e.vehicleIdx)
            EventCode.FLASHBACK -> Quad(RaceEventType.FLASHBACK, "Flashback used")
            EventCode.RED_FLAG -> Quad(RaceEventType.RED_FLAG, "Red flag")
            EventCode.OVERTAKE -> Quad(RaceEventType.OVERTAKE, "${n(e.overtakingVehicleIdx)} passes ${n(e.beingOvertakenVehicleIdx)}", e.overtakingVehicleIdx, e.beingOvertakenVehicleIdx)
            EventCode.SAFETY_CAR -> Quad(RaceEventType.SAFETY_CAR, safetyCarText(e.safetyCarType, e.safetyCarEventType))
            EventCode.COLLISION -> Quad(RaceEventType.COLLISION, "Contact: ${n(e.collisionVehicle1Idx)} and ${n(e.collisionVehicle2Idx)}", e.collisionVehicle1Idx, e.collisionVehicle2Idx)
            EventCode.PARTIAL_MODE_ENABLED -> Quad(RaceEventType.PARTIAL_MODE, "Partial mode enabled — ${drsReason(e.reason)}")
            EventCode.PARTIAL_MODE_DISABLED -> Quad(RaceEventType.PARTIAL_MODE, "Partial mode disabled")
            EventCode.OVERTAKE_MODE_ENABLED -> Quad(RaceEventType.OVERTAKE_MODE, "Overtake mode enabled")
            EventCode.OVERTAKE_MODE_DISABLED -> Quad(RaceEventType.OVERTAKE_MODE, "Overtake mode disabled")
            else -> return
        }
        val event = RaceEvent(
            id = nextId++,
            sessionTime = e.header.sessionTime,
            lap = lap,
            type = type,
            text = text,
            vehicleIndex = a,
            otherVehicleIndex = b,
            involvesPlayer = a == playerIndex || b == playerIndex,
        )
        val current = _events.value
        _events.value = (listOf(event) + current.take(MAX_EVENTS - 1)).toPersistentList()
    }

    fun reset() {
        _events.value = persistentListOf()
    }

    private data class Quad(val type: RaceEventType, val text: String, val a: Int = -1, val b: Int = -1)

    private companion object {
        const val MAX_EVENTS = 100
        const val PENALTY_TIME = 4

        fun lapTime(seconds: Float): String {
            val ms = (seconds * 1000).toLong()
            return String.format(Locale.US, "%d:%02d.%03d", ms / 60_000, ms / 1000 % 60, ms % 1000)
        }

        fun drsReason(r: Int) = when (r) {
            0 -> "wet track"
            1 -> "safety car"
            2 -> "red flag"
            3 -> "minimum lap not reached"
            else -> "race control"
        }

        fun retirementReason(r: Int) = when (r) {
            1 -> "retired"
            3 -> "terminal damage"
            5 -> "not enough laps"
            6 -> "black flagged"
            7 -> "red flagged"
            8 -> "mechanical failure"
            else -> "out"
        }

        fun safetyCarText(type: Int, event: Int): String {
            val car = when (type) {
                2 -> "Virtual Safety Car"
                3 -> "Formation lap"
                else -> "Safety Car"
            }
            return when (event) {
                0 -> "$car deployed"
                1 -> "$car in this lap"
                2 -> "$car returned"
                3 -> "Racing resumes"
                else -> car
            }
        }
    }
}
