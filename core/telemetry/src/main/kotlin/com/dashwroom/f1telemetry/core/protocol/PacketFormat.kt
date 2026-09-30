package com.dashwroom.f1telemetry.core.protocol

/**
 * UDP formats this app understands. The value is the header's `m_packetFormat`.
 *
 * - 2025: "Data Output from F1 25" v3 — 22 car slots.
 * - 2026: "Data Output from F1 25: 2026 Season Pack" v1.2 — 24 car slots, wider ids, extra fields.
 *
 * The Season Pack can also be told to emit "2025" (or "2024") via its "UDP Format" option, so a
 * Season Pack player may legitimately send 2025 packets. Anything else is ignored, never parsed.
 */
enum class PacketFormat(val wireValue: Int, val maxCars: Int, val displayName: String) {
    F1_25(2025, 22, "F1 25"),
    F1_25_SEASON_2026(2026, 24, "F1 25 · 2026 Season Pack");

    companion object {
        /** Largest car array across supported formats; used to size pre-allocated storage. */
        const val MAX_CARS = 24

        fun fromWire(value: Int): PacketFormat? = when (value) {
            2025 -> F1_25
            2026 -> F1_25_SEASON_2026
            else -> null
        }
    }
}
