package com.dashwroom.f1telemetry.core.parser

import com.dashwroom.f1telemetry.core.packet.EventCode
import com.dashwroom.f1telemetry.core.packet.EventPacket
import com.dashwroom.f1telemetry.core.protocol.PacketFormat
import com.dashwroom.f1telemetry.core.protocol.f32
import com.dashwroom.f1telemetry.core.protocol.flag
import com.dashwroom.f1telemetry.core.protocol.u32
import com.dashwroom.f1telemetry.core.protocol.u8
import java.nio.ByteBuffer

/**
 * Packet 3 — `PacketEventData` = header(29) + uint8 m_eventStringCode[4] @29 + `EventDataDetails` @33.
 * 45 bytes in both formats (the union is 12 bytes, sized by SpeedTrap).
 *
 * Union members, offsets relative to @33:
 * ```
 * FTLP  FastestLap              vehicleIdx u8@0, lapTime float@1
 * RTMT  Retirement              vehicleIdx u8@0, reason u8@1
 * DRSD  DRSDisabled             reason u8@0
 * TMPT  TeamMateInPits          vehicleIdx u8@0
 * RCWN  RaceWinner              vehicleIdx u8@0
 * PENA  Penalty                 penaltyType@0 infringementType@1 vehicleIdx@2 otherVehicleIdx@3
 *                               time@4 lapNum@5 placesGained@6 (all u8)
 * SPTP  SpeedTrap               vehicleIdx u8@0, speed float@1, isOverallFastestInSession u8@5,
 *                               isDriverFastestInSession u8@6, fastestVehicleIdxInSession u8@7,
 *                               fastestSpeedInSession float@8
 * STLG  StartLights             numLights u8@0
 * DTSV  DriveThroughPenaltyServed vehicleIdx u8@0
 * SGSV  StopGoPenaltyServed     vehicleIdx u8@0, stopTime float@1
 * FLBK  Flashback               flashbackFrameIdentifier u32@0, flashbackSessionTime float@4
 * BUTN  Buttons                 buttonStatus u32@0
 * OVTK  Overtake                overtakingVehicleIdx u8@0, beingOvertakenVehicleIdx u8@1
 * SCAR  SafetyCar               safetyCarType u8@0, eventType u8@1
 * COLL  Collision               vehicle1Idx u8@0, vehicle2Idx u8@1, severity u8@2 (2026 only)
 * PMEN  PartialModeEnabled      reason u8@0 (2026 only)
 * SSTA SEND DRSE CHQF LGOT RDFL PMDI OVEN OVDI carry no details.
 * ```
 */
internal object EventParser {
    private const val CODE = 29
    private const val D = 33

    fun parse(buf: ByteBuffer, out: EventPacket) {
        val code = (buf.u8(CODE) shl 24) or (buf.u8(CODE + 1) shl 16) or (buf.u8(CODE + 2) shl 8) or buf.u8(CODE + 3)
        out.code = code
        when (code) {
            EventCode.FASTEST_LAP -> {
                out.vehicleIdx = buf.u8(D)
                out.lapTimeSeconds = buf.f32(D + 1)
            }
            EventCode.RETIREMENT -> {
                out.vehicleIdx = buf.u8(D)
                out.reason = buf.u8(D + 1)
            }
            EventCode.DRS_DISABLED, EventCode.PARTIAL_MODE_ENABLED -> out.reason = buf.u8(D)
            EventCode.TEAM_MATE_IN_PITS, EventCode.RACE_WINNER, EventCode.DRIVE_THROUGH_SERVED ->
                out.vehicleIdx = buf.u8(D)
            EventCode.PENALTY -> with(out.penalty) {
                penaltyType = buf.u8(D)
                infringementType = buf.u8(D + 1)
                vehicleIdx = buf.u8(D + 2)
                otherVehicleIdx = buf.u8(D + 3)
                timeSeconds = buf.u8(D + 4)
                lapNum = buf.u8(D + 5)
                placesGained = buf.u8(D + 6)
            }
            EventCode.SPEED_TRAP -> with(out.speedTrap) {
                vehicleIdx = buf.u8(D)
                speedKph = buf.f32(D + 1)
                isOverallFastestInSession = buf.flag(D + 5)
                isDriverFastestInSession = buf.flag(D + 6)
                fastestVehicleIdxInSession = buf.u8(D + 7)
                fastestSpeedInSession = buf.f32(D + 8)
            }
            EventCode.START_LIGHTS -> out.numLights = buf.u8(D)
            EventCode.STOP_GO_SERVED -> {
                out.vehicleIdx = buf.u8(D)
                out.stopTimeSeconds = buf.f32(D + 1)
            }
            EventCode.FLASHBACK -> {
                out.flashbackFrameIdentifier = buf.u32(D)
                out.flashbackSessionTime = buf.f32(D + 4)
            }
            EventCode.BUTTON_STATUS -> out.buttonStatus = buf.u32(D)
            EventCode.OVERTAKE -> {
                out.overtakingVehicleIdx = buf.u8(D)
                out.beingOvertakenVehicleIdx = buf.u8(D + 1)
            }
            EventCode.SAFETY_CAR -> {
                out.safetyCarType = buf.u8(D)
                out.safetyCarEventType = buf.u8(D + 1)
            }
            EventCode.COLLISION -> {
                out.collisionVehicle1Idx = buf.u8(D)
                out.collisionVehicle2Idx = buf.u8(D + 1)
                out.collisionSeverity =
                    if (out.format == PacketFormat.F1_25_SEASON_2026) buf.u8(D + 2) else -1
            }
            else -> Unit // SSTA, SEND, DRSE, CHQF, LGOT, RDFL, PMDI, OVEN, OVDI, or unknown codes.
        }
    }
}
