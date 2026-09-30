package com.dashwroom.f1telemetry.core.packet

import com.dashwroom.f1telemetry.core.protocol.PacketFormat
import com.dashwroom.f1telemetry.core.protocol.PacketId

/**
 * Packet 6 — per-car telemetry. Wheel arrays use the spec's order:
 * 0 = rear left, 1 = rear right, 2 = front left, 3 = front right.
 */
class CarTelemetryPacket : F1Packet {
    override val packetId: Int = PacketId.CAR_TELEMETRY
    override val header = PacketHeader()
    val cars = Array(PacketFormat.MAX_CARS) { CarTelemetry() }

    /** 255 = MFD closed. */
    var mfdPanelIndex = 255
    var mfdPanelIndexSecondaryPlayer = 255

    /** 0 if no gear suggested. */
    var suggestedGear = 0

    class CarTelemetry {
        var speedKph = 0
        var throttle = 0f
        var steer = 0f
        var brake = 0f
        var clutch = 0

        /** 1-8, N = 0, R = -1. */
        var gear = 0
        var engineRpm = 0
        var drs = false
        var revLightsPercent = 0

        /** bit 0 = leftmost LED, bit 14 = rightmost LED. */
        var revLightsBitValue = 0
        val brakesTemperature = IntArray(4)
        val tyresSurfaceTemperature = IntArray(4)
        val tyresInnerTemperature = IntArray(4)

        /** uint16 in 2025, uint8 in 2026. */
        var engineTemperature = 0
        val tyresPressure = FloatArray(4)
        val surfaceType = IntArray(4)
    }
}
