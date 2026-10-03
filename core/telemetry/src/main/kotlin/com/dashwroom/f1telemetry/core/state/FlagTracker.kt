package com.dashwroom.f1telemetry.core.state

import com.dashwroom.f1telemetry.core.model.FiaFlag
import com.dashwroom.f1telemetry.core.model.FlagState
import com.dashwroom.f1telemetry.core.model.SafetyCarMode
import com.dashwroom.f1telemetry.core.packet.EventCode
import com.dashwroom.f1telemetry.core.packet.EventPacket
import com.dashwroom.f1telemetry.core.packet.SessionPacket
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Maintains [FlagState] on the ingest thread. Car Status arrives at 60 Hz, so the per-packet path
 * only compares ints and publishes (allocates a new state) when something actually changed.
 *
 * Red flag: raised by the RDFL event; cleared when the race restarts (lights out, session start,
 * "resume race"), when the player's flag turns green, on a new session, or — as a safety net if
 * the game never says so — [RED_FLAG_TIMEOUT_S] of session time later.
 */
internal class FlagTracker {
    private val _state = MutableStateFlow(FlagState())
    val state: StateFlow<FlagState> = _state.asStateFlow()

    private var rawPlayerFlag = -2
    private var rawSafetyCar = -1
    private var rawYellowZones = -1
    private var redFlagAt = -1f

    fun onPlayerFlag(raw: Int, sessionTime: Float) {
        if (redFlagAt >= 0f && sessionTime - redFlagAt > RED_FLAG_TIMEOUT_S) clearRedFlag()
        if (raw == rawPlayerFlag) return
        rawPlayerFlag = raw
        val flag = when (raw) {
            1 -> FiaFlag.GREEN
            2 -> FiaFlag.BLUE
            3 -> FiaFlag.YELLOW
            else -> FiaFlag.NONE
        }
        val s = _state.value
        if (flag == s.playerFlag) return
        // Green after a yellow (or after a red flag) means "track clear": flash it.
        val resumed = flag == FiaFlag.GREEN && (s.playerFlag == FiaFlag.YELLOW || s.redFlag)
        _state.value = s.copy(
            playerFlag = flag,
            redFlag = if (flag == FiaFlag.GREEN) false else s.redFlag,
            greenCount = if (resumed) s.greenCount + 1 else s.greenCount,
        ).also { if (flag == FiaFlag.GREEN) redFlagAt = -1f }
    }

    fun onSession(p: SessionPacket) {
        var zones = 0
        for (z in 0 until p.numMarshalZones.coerceAtMost(p.marshalZoneFlag.size)) if (p.marshalZoneFlag[z] == 3) zones++
        val sc = p.safetyCarStatus
        if (sc == rawSafetyCar && zones == rawYellowZones) return
        rawSafetyCar = sc
        rawYellowZones = zones
        val mode = when (sc) {
            1 -> SafetyCarMode.FULL
            2 -> SafetyCarMode.VIRTUAL
            3 -> SafetyCarMode.FORMATION_LAP
            else -> SafetyCarMode.NONE
        }
        val s = _state.value
        _state.value = s.copy(
            safetyCar = mode,
            safetyCarEnding = if (mode == SafetyCarMode.NONE || mode != s.safetyCar) false else s.safetyCarEnding,
            yellowZones = zones,
        )
    }

    fun onEvent(e: EventPacket) {
        val s = _state.value
        when (e.code) {
            EventCode.RED_FLAG -> {
                redFlagAt = e.header.sessionTime
                if (!s.redFlag) _state.value = s.copy(redFlag = true)
            }
            EventCode.SAFETY_CAR -> when (e.safetyCarEventType) {
                0 -> { // deployed
                    val mode = when (e.safetyCarType) {
                        2 -> SafetyCarMode.VIRTUAL
                        3 -> SafetyCarMode.FORMATION_LAP
                        else -> SafetyCarMode.FULL
                    }
                    _state.value = s.copy(safetyCar = mode, safetyCarEnding = false)
                }
                1 -> _state.value = s.copy(safetyCarEnding = true) // in this lap
                2 -> _state.value = s.copy(safetyCarEnding = false) // returned to the pits
                3 -> { // resume race
                    redFlagAt = -1f
                    _state.value = s.copy(safetyCar = SafetyCarMode.NONE, safetyCarEnding = false, redFlag = false, greenCount = s.greenCount + 1)
                }
            }
            EventCode.LIGHTS_OUT, EventCode.SESSION_STARTED -> if (s.redFlag) {
                redFlagAt = -1f
                _state.value = s.copy(redFlag = false, greenCount = s.greenCount + 1)
            }
            else -> Unit
        }
    }

    private fun clearRedFlag() {
        redFlagAt = -1f
        val s = _state.value
        if (s.redFlag) _state.value = s.copy(redFlag = false)
    }

    fun reset() {
        rawPlayerFlag = -2
        rawSafetyCar = -1
        rawYellowZones = -1
        redFlagAt = -1f
        _state.value = FlagState()
    }

    private companion object {
        const val RED_FLAG_TIMEOUT_S = 600f
    }
}
