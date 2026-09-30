package com.dashwroom.f1telemetry.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Team accent colours by appendix team id. Unknown teams fall back to the car's livery colour
 * from the Participants packet, then to neutral grey.
 */
object TeamColors {
    private val byBase = mapOf(
        0 to 0xFF27F4D2, // Mercedes
        1 to 0xFFE8002D, // Ferrari
        2 to 0xFF3671C6, // Red Bull Racing
        3 to 0xFF64C4FF, // Williams
        4 to 0xFF229971, // Aston Martin
        5 to 0xFF0093CC, // Alpine
        6 to 0xFF6692FF, // RB
        7 to 0xFFB6BABD, // Haas
        8 to 0xFFFF8000, // McLaren
        9 to 0xFF52E252, // Sauber
    )

    /** '24 and '26 variants map onto the same constructor colours. */
    private val aliases = mapOf(
        185 to 0, 186 to 1, 187 to 2, 188 to 3, 189 to 4, 190 to 5, 191 to 6, 192 to 7, 193 to 8, 194 to 9,
        476 to 0, 477 to 1, 478 to 2, 479 to 3, 480 to 4, 481 to 5, 482 to 6, 483 to 7, 484 to 8,
    )

    private val extra = mapOf(
        485 to 0xFFBB0A30, // Audi '26
        486 to 0xFFC8C8C8, // Cadillac '26
        142 to 0xFF9B59B6, 154 to 0xFF9B59B6, // APXGP
        129 to 0xFFE67E22, 155 to 0xFFE67E22, // Konnersport
    )

    fun of(teamId: Int, livery: Int?): Color {
        val base = aliases[teamId] ?: teamId
        byBase[base]?.let { return Color(it) }
        extra[teamId]?.let { return Color(it) }
        if (livery != null && livery != 0) return Color(0xFF000000 or livery.toLong())
        return Color(0xFF8A93A3)
    }
}
