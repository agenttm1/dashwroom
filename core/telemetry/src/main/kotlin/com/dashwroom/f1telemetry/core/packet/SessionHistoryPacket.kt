package com.dashwroom.f1telemetry.core.packet

import com.dashwroom.f1telemetry.core.protocol.PacketId

/**
 * Packet 11 — lap and tyre-stint history for ONE car ([carIdx]); the game cycles through all cars
 * (about 20 packets/s). Sector minute/millisecond parts are combined into plain ms.
 */
class SessionHistoryPacket : F1Packet {
    override val packetId: Int = PacketId.SESSION_HISTORY
    override val header = PacketHeader()
    var carIdx = 0
    var numLaps = 0
    var numTyreStints = 0
    var bestLapTimeLapNum = 0
    var bestSector1LapNum = 0
    var bestSector2LapNum = 0
    var bestSector3LapNum = 0
    val laps = Array(MAX_LAPS) { LapHistory() }
    val stints = Array(MAX_STINTS) { TyreStint() }

    class LapHistory {
        var lapTimeMs = 0L
        var sector1Ms = 0
        var sector2Ms = 0
        var sector3Ms = 0

        /** 0x01 lap valid, 0x02 S1 valid, 0x04 S2 valid, 0x08 S3 valid. */
        var validFlags = 0
    }

    class TyreStint {
        /** 255 = current tyre. */
        var endLap = 0
        var actualCompound = 0
        var visualCompound = 0
    }

    companion object {
        const val MAX_LAPS = 100
        const val MAX_STINTS = 8
    }
}
