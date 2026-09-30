package com.dashwroom.f1telemetry.core.parser

import com.dashwroom.f1telemetry.core.packet.CarStatusPacket
import com.dashwroom.f1telemetry.core.protocol.PacketFormat
import com.dashwroom.f1telemetry.core.protocol.f32
import com.dashwroom.f1telemetry.core.protocol.flag
import com.dashwroom.f1telemetry.core.protocol.i8
import com.dashwroom.f1telemetry.core.protocol.u16
import com.dashwroom.f1telemetry.core.protocol.u8
import java.nio.ByteBuffer

/**
 * Packet 7 — `PacketCarStatusData` = header(29) + `CarStatusData[N]`.
 * 2025: stride 55 → 29 + 22×55 (1210) = 1239 bytes.
 * 2026: stride 59 → 29 + 24×59 (1416) = 1445 bytes (adds m_ersHarvestLimitPerLap).
 *
 * `CarStatusData`, offsets relative to the car's first byte:
 * ```
 * off  type    field                                2025      2026
 *   0  uint8   m_tractionControl
 *   1  uint8   m_antiLockBrakes
 *   2  uint8   m_fuelMix
 *   3  uint8   m_frontBrakeBias
 *   4  uint8   m_pitLimiterStatus
 *   5  float   m_fuelInTank
 *   9  float   m_fuelCapacity
 *  13  float   m_fuelRemainingLaps
 *  17  uint16  m_maxRPM
 *  19  uint16  m_idleRPM
 *  21  uint8   m_maxGears
 *  22  uint8   m_drsAllowed
 *  23  uint16  m_drsActivationDistance
 *  25  uint8   m_actualTyreCompound
 *  26  uint8   m_visualTyreCompound
 *  27  uint8   m_tyresAgeLaps
 *  28  int8    m_vehicleFIAFlags
 *  29  float   m_enginePowerICE (W)
 *  33  float   m_enginePowerMGUK (W)
 *  37  float   m_ersStoreEnergy (J)
 *  41  uint8   m_ersDeployMode
 *  42  float   m_ersHarvestedThisLapMGUK
 *  46  float   m_ersHarvestedThisLapMGUH
 *  50  float   m_ersHarvestLimitPerLap              —         @50
 *              m_ersDeployedThisLap                 @50       @54
 *              m_networkPaused (uint8)              @54       @58
 *                                          stride   55        59
 * ```
 */
internal object CarStatusParser {
    private const val FIRST_CAR = 29

    fun parse(buf: ByteBuffer, out: CarStatusPacket) {
        val is2026 = out.format == PacketFormat.F1_25_SEASON_2026
        val stride = if (is2026) 59 else 55
        for (i in 0 until out.numCars) {
            val b = FIRST_CAR + i * stride
            val car = out.cars[i]
            car.tractionControl = buf.u8(b + 0)
            car.antiLockBrakes = buf.flag(b + 1)
            car.fuelMix = buf.u8(b + 2)
            car.frontBrakeBias = buf.u8(b + 3)
            car.pitLimiterOn = buf.flag(b + 4)
            car.fuelInTank = buf.f32(b + 5)
            car.fuelCapacity = buf.f32(b + 9)
            car.fuelRemainingLaps = buf.f32(b + 13)
            car.maxRpm = buf.u16(b + 17)
            car.idleRpm = buf.u16(b + 19)
            car.maxGears = buf.u8(b + 21)
            car.drsAllowed = buf.flag(b + 22)
            car.drsActivationDistance = buf.u16(b + 23)
            car.actualTyreCompound = buf.u8(b + 25)
            car.visualTyreCompound = buf.u8(b + 26)
            car.tyresAgeLaps = buf.u8(b + 27)
            car.vehicleFiaFlags = buf.i8(b + 28)
            car.enginePowerIce = buf.f32(b + 29)
            car.enginePowerMguk = buf.f32(b + 33)
            car.ersStoreEnergy = buf.f32(b + 37)
            car.ersDeployMode = buf.u8(b + 41)
            car.ersHarvestedThisLapMguk = buf.f32(b + 42)
            car.ersHarvestedThisLapMguh = buf.f32(b + 46)
            if (is2026) {
                car.ersHarvestLimitPerLap = buf.f32(b + 50)
                car.ersDeployedThisLap = buf.f32(b + 54)
                car.networkPaused = buf.flag(b + 58)
            } else {
                car.ersHarvestLimitPerLap = Float.NaN
                car.ersDeployedThisLap = buf.f32(b + 50)
                car.networkPaused = buf.flag(b + 54)
            }
        }
    }
}
