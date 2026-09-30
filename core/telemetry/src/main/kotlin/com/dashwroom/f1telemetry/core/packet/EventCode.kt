package com.dashwroom.f1telemetry.core.packet

/** Event string codes, packed as `b0<<24 | b1<<16 | b2<<8 | b3` so matching never allocates. */
object EventCode {
    val SESSION_STARTED = pack("SSTA")
    val SESSION_ENDED = pack("SEND")
    val FASTEST_LAP = pack("FTLP")
    val RETIREMENT = pack("RTMT")
    val DRS_ENABLED = pack("DRSE")
    val DRS_DISABLED = pack("DRSD")
    val TEAM_MATE_IN_PITS = pack("TMPT")
    val CHEQUERED_FLAG = pack("CHQF")
    val RACE_WINNER = pack("RCWN")
    val PENALTY = pack("PENA")
    val SPEED_TRAP = pack("SPTP")
    val START_LIGHTS = pack("STLG")
    val LIGHTS_OUT = pack("LGOT")
    val DRIVE_THROUGH_SERVED = pack("DTSV")
    val STOP_GO_SERVED = pack("SGSV")
    val FLASHBACK = pack("FLBK")
    val BUTTON_STATUS = pack("BUTN")
    val RED_FLAG = pack("RDFL")
    val OVERTAKE = pack("OVTK")
    val SAFETY_CAR = pack("SCAR")
    val COLLISION = pack("COLL")

    // 2026 Season Pack race-control messages.
    val PARTIAL_MODE_ENABLED = pack("PMEN")
    val PARTIAL_MODE_DISABLED = pack("PMDI")
    val OVERTAKE_MODE_ENABLED = pack("OVEN")
    val OVERTAKE_MODE_DISABLED = pack("OVDI")

    fun pack(code: String): Int {
        require(code.length == 4) { "Event codes are 4 characters: $code" }
        return (code[0].code shl 24) or (code[1].code shl 16) or (code[2].code shl 8) or code[3].code
    }

    fun unpack(code: Int): String = buildString(4) {
        append(((code ushr 24) and 0xFF).toChar())
        append(((code ushr 16) and 0xFF).toChar())
        append(((code ushr 8) and 0xFF).toChar())
        append((code and 0xFF).toChar())
    }
}
