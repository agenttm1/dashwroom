package com.dashwroom.f1telemetry.core.parser

import com.dashwroom.f1telemetry.core.packet.CarDamagePacket
import com.dashwroom.f1telemetry.core.protocol.f32
import com.dashwroom.f1telemetry.core.protocol.flag
import com.dashwroom.f1telemetry.core.protocol.u8
import java.nio.ByteBuffer

/**
 * Packet 10 — `PacketCarDamageData` = header(29) + `CarDamageData[N]`, 46 bytes per car in both formats.
 * 2025: 29 + 22×46 (1012) = 1041 bytes.   2026: 29 + 24×46 (1104) = 1133 bytes.
 *
 * `CarDamageData`, offsets relative to the car's first byte:
 * ```
 *  0  float[4] m_tyresWear (%)          16 uint8[4] m_tyresDamage (%)
 * 20  uint8[4] m_brakesDamage (%)       24 uint8[4] m_tyreBlisters (%)
 * 28  uint8 m_frontLeftWingDamage       29 uint8 m_frontRightWingDamage
 * 30  uint8 m_rearWingDamage            31 uint8 m_floorDamage
 * 32  uint8 m_diffuserDamage            33 uint8 m_sidepodDamage
 * 34  uint8 m_drsFault                  35 uint8 m_ersFault
 * 36  uint8 m_gearBoxDamage             37 uint8 m_engineDamage
 * 38  uint8 m_engineMGUHWear            39 uint8 m_engineESWear
 * 40  uint8 m_engineCEWear              41 uint8 m_engineICEWear
 * 42  uint8 m_engineMGUKWear            43 uint8 m_engineTCWear
 * 44  uint8 m_engineBlown               45 uint8 m_engineSeized
 * ```
 */
internal object CarDamageParser {
    private const val FIRST_CAR = 29
    private const val STRIDE = 46

    fun parse(buf: ByteBuffer, out: CarDamagePacket) {
        for (i in 0 until out.numCars) {
            val b = FIRST_CAR + i * STRIDE
            val c = out.cars[i]
            for (w in 0 until 4) {
                c.tyresWear[w] = buf.f32(b + w * 4)
                c.tyresDamage[w] = buf.u8(b + 16 + w)
                c.brakesDamage[w] = buf.u8(b + 20 + w)
                c.tyreBlisters[w] = buf.u8(b + 24 + w)
            }
            c.frontLeftWingDamage = buf.u8(b + 28)
            c.frontRightWingDamage = buf.u8(b + 29)
            c.rearWingDamage = buf.u8(b + 30)
            c.floorDamage = buf.u8(b + 31)
            c.diffuserDamage = buf.u8(b + 32)
            c.sidepodDamage = buf.u8(b + 33)
            c.drsFault = buf.flag(b + 34)
            c.ersFault = buf.flag(b + 35)
            c.gearBoxDamage = buf.u8(b + 36)
            c.engineDamage = buf.u8(b + 37)
            c.engineMguhWear = buf.u8(b + 38)
            c.engineEsWear = buf.u8(b + 39)
            c.engineCeWear = buf.u8(b + 40)
            c.engineIceWear = buf.u8(b + 41)
            c.engineMgukWear = buf.u8(b + 42)
            c.engineTcWear = buf.u8(b + 43)
            c.engineBlown = buf.flag(b + 44)
            c.engineSeized = buf.flag(b + 45)
        }
    }
}
