package com.dashwroom.f1telemetry.core.packet

import com.dashwroom.f1telemetry.core.protocol.PacketId

/**
 * Packet 3 — a notable event. [code] holds the 4 ASCII bytes packed big-endian (see [EventCode]);
 * only the detail group matching the code is filled in, mirroring the spec's C union.
 */
class EventPacket : F1Packet {
    override val packetId: Int = PacketId.EVENT
    override val header = PacketHeader()
    var code = 0

    /** Vehicle index for FTLP, RTMT, TMPT, RCWN, DTSV, SGSV. */
    var vehicleIdx = 255

    /** RTMT result reason / DRSD reason / PMEN reason. */
    var reason = 0

    /** FTLP lap time in seconds. */
    var lapTimeSeconds = 0f

    /** SGSV stop time in seconds. */
    var stopTimeSeconds = 0f

    /** STLG. */
    var numLights = 0
    val penalty = Penalty()
    val speedTrap = SpeedTrap()
    var flashbackFrameIdentifier = 0L
    var flashbackSessionTime = 0f
    var buttonStatus = 0L
    var overtakingVehicleIdx = 255
    var beingOvertakenVehicleIdx = 255

    /** 0 = none, 1 = full, 2 = virtual, 3 = formation lap. */
    var safetyCarType = 0

    /** 0 = deployed, 1 = returning, 2 = returned, 3 = resume race. */
    var safetyCarEventType = 0
    var collisionVehicle1Idx = 255
    var collisionVehicle2Idx = 255

    /** 2026 only: 0 = low, 1 = medium, 2 = high; -1 when not sent. */
    var collisionSeverity = -1

    class Penalty {
        var penaltyType = 0
        var infringementType = 0
        var vehicleIdx = 255
        var otherVehicleIdx = 255
        var timeSeconds = 0
        var lapNum = 0
        var placesGained = 0
    }

    class SpeedTrap {
        var vehicleIdx = 255
        var speedKph = 0f
        var isOverallFastestInSession = false
        var isDriverFastestInSession = false
        var fastestVehicleIdxInSession = 255
        var fastestSpeedInSession = 0f
    }
}
