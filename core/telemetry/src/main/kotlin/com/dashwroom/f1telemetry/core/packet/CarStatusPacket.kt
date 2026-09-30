package com.dashwroom.f1telemetry.core.packet

import com.dashwroom.f1telemetry.core.protocol.PacketFormat
import com.dashwroom.f1telemetry.core.protocol.PacketId

/**
 * Packet 7 — per-car status. Fields hidden by another player's "Your Telemetry: Restricted"
 * setting arrive as zero (fuel, ERS, brake bias, engine power).
 */
class CarStatusPacket : F1Packet {
    override val packetId: Int = PacketId.CAR_STATUS
    override val header = PacketHeader()
    val cars = Array(PacketFormat.MAX_CARS) { CarStatus() }

    class CarStatus {
        /** 0 = off, 1 = medium, 2 = full. */
        var tractionControl = 0
        var antiLockBrakes = false

        /** 0 = lean, 1 = standard, 2 = rich, 3 = max. */
        var fuelMix = 0
        var frontBrakeBias = 0
        var pitLimiterOn = false
        var fuelInTank = 0f
        var fuelCapacity = 0f
        var fuelRemainingLaps = 0f
        var maxRpm = 0
        var idleRpm = 0
        var maxGears = 0
        var drsAllowed = false

        /** 0 = DRS not available, otherwise metres until it is. */
        var drsActivationDistance = 0
        var actualTyreCompound = 0
        var visualTyreCompound = 0
        var tyresAgeLaps = 0

        /** -1 = invalid/unknown, 0 = none, 1 = green, 2 = blue, 3 = yellow. */
        var vehicleFiaFlags = -1
        var enginePowerIce = 0f
        var enginePowerMguk = 0f

        /** Joules. */
        var ersStoreEnergy = 0f

        /** 0 = none, 1 = medium, 2 = hotlap, 3 = overtake (2025) / boost (2026). */
        var ersDeployMode = 0
        var ersHarvestedThisLapMguk = 0f
        var ersHarvestedThisLapMguh = 0f

        /** 2026 only; NaN when decoded from a 2025 packet. */
        var ersHarvestLimitPerLap = Float.NaN
        var ersDeployedThisLap = 0f
        var networkPaused = false
    }
}
