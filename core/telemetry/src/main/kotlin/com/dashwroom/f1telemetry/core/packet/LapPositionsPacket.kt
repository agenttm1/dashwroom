package com.dashwroom.f1telemetry.core.packet

import com.dashwroom.f1telemetry.core.protocol.PacketFormat
import com.dashwroom.f1telemetry.core.protocol.PacketId

/**
 * Packet 15 — each car's position at the start of each lap, for a lap chart. Holds up to
 * [MAX_LAPS] laps starting at lap index [lapStart] (0-based); position 0 = no record.
 */
class LapPositionsPacket : F1Packet {
    override val packetId: Int = PacketId.LAP_POSITIONS
    override val header = PacketHeader()
    var numLaps = 0
    var lapStart = 0

    /** [lap * MAX_CARS + vehicle] — lap is relative to [lapStart]. */
    val positions = IntArray(MAX_LAPS * PacketFormat.MAX_CARS)

    fun position(lap: Int, vehicle: Int): Int = positions[lap * PacketFormat.MAX_CARS + vehicle]

    companion object {
        const val MAX_LAPS = 50
    }
}
