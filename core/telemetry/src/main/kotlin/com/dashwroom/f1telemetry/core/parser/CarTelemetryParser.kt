package com.dashwroom.f1telemetry.core.parser

import com.dashwroom.f1telemetry.core.packet.CarTelemetryPacket
import com.dashwroom.f1telemetry.core.protocol.PacketFormat
import com.dashwroom.f1telemetry.core.protocol.f32
import com.dashwroom.f1telemetry.core.protocol.flag
import com.dashwroom.f1telemetry.core.protocol.i8
import com.dashwroom.f1telemetry.core.protocol.u16
import com.dashwroom.f1telemetry.core.protocol.u8
import java.nio.ByteBuffer

/**
 * Packet 6 — `PacketCarTelemetryData` = header(29) + `CarTelemetryData[N]` + 3 bytes.
 * 2025: stride 60 → 29 + 22×60 (1320) + 3 = 1352 bytes.
 * 2026: stride 59 → 29 + 24×59 (1416) + 3 = 1448 bytes (engine temperature shrank to uint8).
 *
 * `CarTelemetryData`, offsets relative to the car's first byte:
 * ```
 * off  type       field                               2025         2026
 *   0  uint16     m_speed (km/h)
 *   2  float      m_throttle (0..1)
 *   6  float      m_steer (-1..1)
 *  10  float      m_brake (0..1)
 *  14  uint8      m_clutch (0..100)
 *  15  int8       m_gear (1-8, N=0, R=-1)
 *  16  uint16     m_engineRPM
 *  18  uint8      m_drs
 *  19  uint8      m_revLightsPercent
 *  20  uint16     m_revLightsBitValue
 *  22  uint16[4]  m_brakesTemperature (°C)
 *  30  uint8[4]   m_tyresSurfaceTemperature (°C)
 *  34  uint8[4]   m_tyresInnerTemperature (°C)
 *  38             m_engineTemperature (°C)            uint16@38    uint8@38
 *                 m_tyresPressure float[4] (PSI)      @40          @39
 *                 m_surfaceType uint8[4]              @56          @55
 *                                             stride  60           59
 * ```
 * After the array (@ 29 + N×stride): uint8 m_mfdPanelIndex, uint8 m_mfdPanelIndexSecondaryPlayer,
 * int8 m_suggestedGear.
 */
internal object CarTelemetryParser {
    private const val FIRST_CAR = 29

    fun parse(buf: ByteBuffer, out: CarTelemetryPacket) {
        val is2026 = out.format == PacketFormat.F1_25_SEASON_2026
        val stride = if (is2026) 59 else 60
        val pressureOffset = if (is2026) 39 else 40
        val surfaceOffset = if (is2026) 55 else 56
        val n = out.numCars
        for (i in 0 until n) {
            val b = FIRST_CAR + i * stride
            val car = out.cars[i]
            car.speedKph = buf.u16(b + 0)
            car.throttle = buf.f32(b + 2)
            car.steer = buf.f32(b + 6)
            car.brake = buf.f32(b + 10)
            car.clutch = buf.u8(b + 14)
            car.gear = buf.i8(b + 15)
            car.engineRpm = buf.u16(b + 16)
            car.drs = buf.flag(b + 18)
            car.revLightsPercent = buf.u8(b + 19)
            car.revLightsBitValue = buf.u16(b + 20)
            for (w in 0 until 4) {
                car.brakesTemperature[w] = buf.u16(b + 22 + w * 2)
                car.tyresSurfaceTemperature[w] = buf.u8(b + 30 + w)
                car.tyresInnerTemperature[w] = buf.u8(b + 34 + w)
                car.tyresPressure[w] = buf.f32(b + pressureOffset + w * 4)
                car.surfaceType[w] = buf.u8(b + surfaceOffset + w)
            }
            car.engineTemperature = if (is2026) buf.u8(b + 38) else buf.u16(b + 38)
        }
        val tail = FIRST_CAR + n * stride
        out.mfdPanelIndex = buf.u8(tail)
        out.mfdPanelIndexSecondaryPlayer = buf.u8(tail + 1)
        out.suggestedGear = buf.i8(tail + 2)
    }
}
