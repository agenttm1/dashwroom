package com.dashwroom.f1telemetry.core.protocol

/**
 * Exact datagram sizes ("Size: N bytes") per packet id, straight from each spec document.
 * A datagram whose length differs is rejected rather than half-parsed.
 */
object PacketSizes {
    const val HEADER = 29

    /** Largest packet in any supported format (2026 Participants, 1470 bytes) plus headroom. */
    const val MAX_DATAGRAM = 2048

    const val NOT_IN_FORMAT = -1

    //                              0     1     2   3     4     5     6     7     8    9    10    11   12   13   14    15    16
    private val f125 = intArrayOf(1349, 753, 1285, 45, 1284, 1133, 1352, 1239, 1042, 954, 1041, 1460, 231, 273, 101, 1131, NOT_IN_FORMAT)
    private val f126 = intArrayOf(1325, 926, 1399, 45, 1470, 1233, 1448, 1445, 1134, 1062, 1133, 1460, 231, 273, 104, 1231, 269)

    fun expected(format: PacketFormat, packetId: Int): Int {
        if (packetId !in 0 until PacketId.COUNT) return NOT_IN_FORMAT
        return when (format) {
            PacketFormat.F1_25 -> f125[packetId]
            PacketFormat.F1_25_SEASON_2026 -> f126[packetId]
        }
    }
}
