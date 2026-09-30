package com.dashwroom.f1telemetry.core.protocol

/** `m_packetId` values from the spec's "Packet IDs" table. */
object PacketId {
    const val MOTION = 0
    const val SESSION = 1
    const val LAP_DATA = 2
    const val EVENT = 3
    const val PARTICIPANTS = 4
    const val CAR_SETUPS = 5
    const val CAR_TELEMETRY = 6
    const val CAR_STATUS = 7
    const val FINAL_CLASSIFICATION = 8
    const val LOBBY_INFO = 9
    const val CAR_DAMAGE = 10
    const val SESSION_HISTORY = 11
    const val TYRE_SETS = 12
    const val MOTION_EX = 13
    const val TIME_TRIAL = 14
    const val LAP_POSITIONS = 15

    /** 2026 Season Pack only. */
    const val CAR_TELEMETRY_2 = 16

    /** Number of id slots we track (0..16). */
    const val COUNT = 17

    private val names = arrayOf(
        "Motion", "Session", "Lap Data", "Event", "Participants", "Car Setups",
        "Car Telemetry", "Car Status", "Final Classification", "Lobby Info", "Car Damage",
        "Session History", "Tyre Sets", "Motion Ex", "Time Trial", "Lap Positions",
        "Car Telemetry 2",
    )

    fun name(id: Int): String = if (id in 0 until COUNT) names[id] else "Unknown ($id)"
}
