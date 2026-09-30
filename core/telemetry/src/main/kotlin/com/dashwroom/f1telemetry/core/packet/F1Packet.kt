package com.dashwroom.f1telemetry.core.packet

import com.dashwroom.f1telemetry.core.protocol.PacketFormat

/**
 * One decoded UDP packet.
 *
 * These are deliberately *mutable, pre-allocated* holders rather than data classes: the game
 * sends thousands of packets a second, and allocating a fresh object graph per packet would
 * churn the GC. [com.dashwroom.f1telemetry.core.parser.PacketParser] owns exactly one instance
 * of each type and overwrites it in place, so a returned packet is only valid until the next
 * call to `parse` — consumers copy what they need immediately, on the same thread.
 */
sealed interface F1Packet {
    val packetId: Int
    val header: PacketHeader

    /** Format the packet was decoded with; decides car count and field widths. */
    val format: PacketFormat
        get() = header.format

    /** Car slots valid in this packet's arrays (22 for 2025, 24 for 2026). */
    val numCars: Int
        get() = header.format.maxCars
}
