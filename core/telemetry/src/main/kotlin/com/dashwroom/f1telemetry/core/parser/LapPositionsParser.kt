package com.dashwroom.f1telemetry.core.parser

import com.dashwroom.f1telemetry.core.packet.LapPositionsPacket
import com.dashwroom.f1telemetry.core.protocol.PacketFormat
import com.dashwroom.f1telemetry.core.protocol.u8
import java.nio.ByteBuffer

/**
 * Packet 15 — `PacketLapPositionsData`:
 * ```
 * 29 uint8 m_numLaps   30 uint8 m_lapStart (0-indexed)
 * 31 uint8 m_positionForVehicleIdx[50][N]   — row per lap, N cars per row (0 = no record)
 * ```
 * 2025: 31 + 50×22 = 1131 bytes.   2026: 31 + 50×24 = 1231 bytes.
 */
internal object LapPositionsParser {
    private const val GRID = 31

    fun parse(buf: ByteBuffer, out: LapPositionsPacket) {
        val n = out.numCars
        out.numLaps = buf.u8(29).coerceAtMost(LapPositionsPacket.MAX_LAPS)
        out.lapStart = buf.u8(30)
        for (lap in 0 until LapPositionsPacket.MAX_LAPS) {
            for (car in 0 until PacketFormat.MAX_CARS) {
                out.positions[lap * PacketFormat.MAX_CARS + car] = if (car < n) buf.u8(GRID + lap * n + car) else 0
            }
        }
    }
}
