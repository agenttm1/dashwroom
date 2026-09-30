package com.dashwroom.f1telemetry.core.packet

import com.dashwroom.f1telemetry.core.protocol.PacketFormat
import com.dashwroom.f1telemetry.core.protocol.PacketId

/** Packet 0 — physics data for every car. Direction vectors and g-forces are pre-scaled to floats. */
class MotionPacket : F1Packet {
    override val packetId: Int = PacketId.MOTION
    override val header = PacketHeader()
    val cars = Array(PacketFormat.MAX_CARS) { CarMotion() }

    class CarMotion {
        var worldPositionX = 0f
        var worldPositionY = 0f
        var worldPositionZ = 0f
        var worldVelocityX = 0f
        var worldVelocityY = 0f
        var worldVelocityZ = 0f

        /** Normalised; decoded from int16 by dividing by 32767. */
        var worldForwardDirX = 0f
        var worldForwardDirY = 0f
        var worldForwardDirZ = 0f
        var worldRightDirX = 0f
        var worldRightDirY = 0f
        var worldRightDirZ = 0f

        /** In g. 2025 sends floats; 2026 sends int16 quantised ×1000. */
        var gForceLateral = 0f
        var gForceLongitudinal = 0f
        var gForceVertical = 0f
        var yaw = 0f
        var pitch = 0f
        var roll = 0f
    }
}
