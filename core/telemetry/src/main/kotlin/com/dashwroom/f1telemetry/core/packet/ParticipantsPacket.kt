package com.dashwroom.f1telemetry.core.packet

import com.dashwroom.f1telemetry.core.protocol.PacketFormat
import com.dashwroom.f1telemetry.core.protocol.PacketId

/** Packet 4 — participant list, sent every 5 seconds. Index by vehicle index. */
class ParticipantsPacket : F1Packet {
    override val packetId: Int = PacketId.PARTICIPANTS
    override val header = PacketHeader()
    var numActiveCars = 0
    val cars = Array(PacketFormat.MAX_CARS) { Participant() }

    class Participant {
        var aiControlled = false

        /** 255 (2025) / 65535 (2026) for a network human. */
        var driverId = 0
        var networkId = 0
        var teamId = 0
        var myTeam = false
        var raceNumber = 0
        var nationality = 0

        /** Raw null-terminated UTF-8 bytes; [name] is only re-decoded when these change. */
        val nameBytes = ByteArray(NAME_LENGTH)
        var nameLength = 0
        var name: String = ""
        var yourTelemetryPublic = false
        var showOnlineNames = false
        var techLevel = 0

        /** 1 = Steam, 3 = PlayStation, 4 = Xbox, 6 = Origin, 255 = unknown. */
        var platform = 255
        var numColours = 0

        /** Packed 0xRRGGBB. */
        val liveryColours = IntArray(MAX_LIVERY_COLOURS)
    }

    companion object {
        const val NAME_LENGTH = 32
        const val MAX_LIVERY_COLOURS = 4
    }
}
