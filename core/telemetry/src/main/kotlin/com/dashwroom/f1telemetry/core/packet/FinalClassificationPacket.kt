package com.dashwroom.f1telemetry.core.packet

import com.dashwroom.f1telemetry.core.protocol.PacketFormat
import com.dashwroom.f1telemetry.core.protocol.PacketId

/** Packet 8 — final classification, sent once at the end of a race. */
class FinalClassificationPacket : F1Packet {
    override val packetId: Int = PacketId.FINAL_CLASSIFICATION
    override val header = PacketHeader()
    var numClassified = 0
    val cars = Array(PacketFormat.MAX_CARS) { Result() }

    class Result {
        var position = 0
        var numLaps = 0
        var gridPosition = 0
        var points = 0
        var numPitStops = 0
        var resultStatus = 0
        var resultReason = 0
        var bestLapTimeMs = 0L
        var totalRaceTimeSeconds = 0.0
        var penaltiesTimeSeconds = 0
        var numPenalties = 0
        var numTyreStints = 0
        val tyreStintsActual = IntArray(MAX_STINTS)
        val tyreStintsVisual = IntArray(MAX_STINTS)
        val tyreStintsEndLaps = IntArray(MAX_STINTS)
    }

    companion object {
        const val MAX_STINTS = 8
    }
}
