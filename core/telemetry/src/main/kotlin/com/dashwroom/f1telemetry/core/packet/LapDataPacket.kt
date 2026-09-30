package com.dashwroom.f1telemetry.core.packet

import com.dashwroom.f1telemetry.core.protocol.PacketFormat
import com.dashwroom.f1telemetry.core.protocol.PacketId

/** Packet 2 — timing for every car. Minute/millisecond split fields are combined into plain ms. */
class LapDataPacket : F1Packet {
    override val packetId: Int = PacketId.LAP_DATA
    override val header = PacketHeader()
    val cars = Array(PacketFormat.MAX_CARS) { LapData() }

    /** 255 if invalid. */
    var timeTrialPbCarIdx = 255
    var timeTrialRivalCarIdx = 255

    class LapData {
        var lastLapTimeMs = 0L
        var currentLapTimeMs = 0L
        var sector1TimeMs = 0
        var sector2TimeMs = 0
        var deltaToCarInFrontMs = 0
        var deltaToRaceLeaderMs = 0
        var lapDistance = 0f
        var totalDistance = 0f
        var safetyCarDelta = 0f
        var carPosition = 0
        var currentLapNum = 0

        /** 0 = none, 1 = pitting, 2 = in pit area. */
        var pitStatus = 0
        var numPitStops = 0

        /** 0 = sector 1, 1 = sector 2, 2 = sector 3. */
        var sector = 0
        var currentLapInvalid = false
        var penaltiesSeconds = 0
        var totalWarnings = 0
        var cornerCuttingWarnings = 0
        var numUnservedDriveThroughPens = 0
        var numUnservedStopGoPens = 0
        var gridPosition = 0

        /** 0 = in garage, 1 = flying lap, 2 = in lap, 3 = out lap, 4 = on track. */
        var driverStatus = 0

        /** 0 = invalid, 1 = inactive, 2 = active, 3 = finished, 4 = DNF, 5 = DSQ, 6 = not classified, 7 = retired. */
        var resultStatus = 0
        var pitLaneTimerActive = false
        var pitLaneTimeInLaneMs = 0
        var pitStopTimerMs = 0
        var pitStopShouldServePen = false
        var speedTrapFastestSpeedKph = 0f

        /** 255 = not set. */
        var speedTrapFastestLap = 255
    }
}
