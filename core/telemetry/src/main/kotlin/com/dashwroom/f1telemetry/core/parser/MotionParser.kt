package com.dashwroom.f1telemetry.core.parser

import com.dashwroom.f1telemetry.core.packet.MotionPacket
import com.dashwroom.f1telemetry.core.protocol.PacketFormat
import com.dashwroom.f1telemetry.core.protocol.f32
import com.dashwroom.f1telemetry.core.protocol.i16
import java.nio.ByteBuffer

/**
 * Packet 0 — `PacketMotionData` = header(29) + `CarMotionData[N]`.
 * 2025: N = 22, stride 60 → 29 + 1320 = 1349 bytes.
 * 2026: N = 24, stride 54 → 29 + 1296 = 1325 bytes (g-forces quantised to int16).
 *
 * `CarMotionData`, offsets relative to the car's first byte:
 * ```
 * off  type   field                          2025        2026
 *   0  float  m_worldPositionX (m)
 *   4  float  m_worldPositionY
 *   8  float  m_worldPositionZ
 *  12  float  m_worldVelocityX (m/s)
 *  16  float  m_worldVelocityY
 *  20  float  m_worldVelocityZ
 *  24  int16  m_worldForwardDirX  (÷32767)
 *  26  int16  m_worldForwardDirY
 *  28  int16  m_worldForwardDirZ
 *  30  int16  m_worldRightDirX    (÷32767)
 *  32  int16  m_worldRightDirY
 *  34  int16  m_worldRightDirZ
 *  36         m_gForceLateral                float@36    int16@36 (÷1000)
 *             m_gForceLongitudinal           float@40    int16@38
 *             m_gForceVertical               float@44    int16@40
 *             m_yaw (rad)                    float@48    float@42
 *             m_pitch                        float@52    float@46
 *             m_roll                         float@56    float@50
 *                                    stride  60          54
 * ```
 */
internal object MotionParser {
    private const val FIRST_CAR = 29
    private const val DIR_SCALE = 1f / 32767f
    private const val G_SCALE_2026 = 1f / 1000f

    fun parse(buf: ByteBuffer, out: MotionPacket) {
        val quantisedG = out.format == PacketFormat.F1_25_SEASON_2026
        val stride = if (quantisedG) 54 else 60
        for (i in 0 until out.numCars) {
            val base = FIRST_CAR + i * stride
            val car = out.cars[i]
            car.worldPositionX = buf.f32(base + 0)
            car.worldPositionY = buf.f32(base + 4)
            car.worldPositionZ = buf.f32(base + 8)
            car.worldVelocityX = buf.f32(base + 12)
            car.worldVelocityY = buf.f32(base + 16)
            car.worldVelocityZ = buf.f32(base + 20)
            car.worldForwardDirX = buf.i16(base + 24) * DIR_SCALE
            car.worldForwardDirY = buf.i16(base + 26) * DIR_SCALE
            car.worldForwardDirZ = buf.i16(base + 28) * DIR_SCALE
            car.worldRightDirX = buf.i16(base + 30) * DIR_SCALE
            car.worldRightDirY = buf.i16(base + 32) * DIR_SCALE
            car.worldRightDirZ = buf.i16(base + 34) * DIR_SCALE
            if (quantisedG) {
                car.gForceLateral = buf.i16(base + 36) * G_SCALE_2026
                car.gForceLongitudinal = buf.i16(base + 38) * G_SCALE_2026
                car.gForceVertical = buf.i16(base + 40) * G_SCALE_2026
                car.yaw = buf.f32(base + 42)
                car.pitch = buf.f32(base + 46)
                car.roll = buf.f32(base + 50)
            } else {
                car.gForceLateral = buf.f32(base + 36)
                car.gForceLongitudinal = buf.f32(base + 40)
                car.gForceVertical = buf.f32(base + 44)
                car.yaw = buf.f32(base + 48)
                car.pitch = buf.f32(base + 52)
                car.roll = buf.f32(base + 56)
            }
        }
    }
}
