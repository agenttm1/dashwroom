package com.dashwroom.f1telemetry.core.packet

import com.dashwroom.f1telemetry.core.protocol.PacketFormat
import com.dashwroom.f1telemetry.core.protocol.PacketId

/** Packet 16 (2026 Season Pack only) — active aero, overtake mode, regulations, wrong-way. */
class CarTelemetry2Packet : F1Packet {
    override val packetId: Int = PacketId.CAR_TELEMETRY_2
    override val header = PacketHeader()
    val cars = Array(PacketFormat.MAX_CARS) { CarTelemetry2() }

    class CarTelemetry2 {
        /** 0 = corner mode, 1 = straight mode. */
        var activeAeroMode = 0
        var activeAeroAvailable = false
        var activeAeroActivationDistance = 0
        var overtakeAvailable = false
        var overtakeActive = false
        var overtakeActivationDistance = 0
        var regulations2026 = false
        var drivingWrongWay = false
    }
}
