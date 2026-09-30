package com.dashwroom.f1telemetry.core.packet

import com.dashwroom.f1telemetry.core.protocol.PacketId

/** Packet 12 — the tyre-set inventory for ONE car: 13 dry + 7 wet sets. */
class TyreSetsPacket : F1Packet {
    override val packetId: Int = PacketId.TYRE_SETS
    override val header = PacketHeader()
    var carIdx = 0
    val sets = Array(MAX_SETS) { TyreSet() }
    var fittedIdx = 0

    class TyreSet {
        var actualCompound = 0
        var visualCompound = 0
        var wearPercent = 0
        var available = false
        var recommendedSession = 0
        var lifeSpanLaps = 0
        var usableLifeLaps = 0

        /** Lap-time delta vs the fitted set, ms (int16). */
        var lapDeltaTimeMs = 0
        var fitted = false
    }

    companion object {
        const val MAX_SETS = 20
    }
}
