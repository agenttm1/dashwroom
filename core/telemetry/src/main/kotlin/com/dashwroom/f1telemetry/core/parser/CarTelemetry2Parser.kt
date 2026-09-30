package com.dashwroom.f1telemetry.core.parser

import com.dashwroom.f1telemetry.core.packet.CarTelemetry2Packet
import com.dashwroom.f1telemetry.core.protocol.flag
import com.dashwroom.f1telemetry.core.protocol.u16
import com.dashwroom.f1telemetry.core.protocol.u8
import java.nio.ByteBuffer

/**
 * Packet 16 (2026 only) — `PacketCarTelemetry2Data` = header(29) + `CarTelemetry2Data[24]`, 10 bytes each
 * → 29 + 240 = 269 bytes.
 * ```
 * 0 uint8 m_activeAeroMode   1 uint8 m_activeAeroAvailable   2 uint16 m_activeAeroActivationDistance
 * 4 uint8 m_overtakeAvailable 5 uint8 m_overtakeActive       6 uint16 m_overtakeActivationDistance
 * 8 uint8 m_2026Regulations  9 uint8 m_drivingWrongWay
 * ```
 */
internal object CarTelemetry2Parser {
    private const val FIRST_CAR = 29
    private const val STRIDE = 10

    fun parse(buf: ByteBuffer, out: CarTelemetry2Packet) {
        for (i in 0 until out.numCars) {
            val b = FIRST_CAR + i * STRIDE
            val c = out.cars[i]
            c.activeAeroMode = buf.u8(b)
            c.activeAeroAvailable = buf.flag(b + 1)
            c.activeAeroActivationDistance = buf.u16(b + 2)
            c.overtakeAvailable = buf.flag(b + 4)
            c.overtakeActive = buf.flag(b + 5)
            c.overtakeActivationDistance = buf.u16(b + 6)
            c.regulations2026 = buf.flag(b + 8)
            c.drivingWrongWay = buf.flag(b + 9)
        }
    }
}
