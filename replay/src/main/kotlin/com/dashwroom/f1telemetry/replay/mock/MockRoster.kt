package com.dashwroom.f1telemetry.replay.mock

import com.dashwroom.f1telemetry.core.protocol.PacketFormat

/** One synthetic entrant. Ids come from the spec appendix tables. */
class MockEntrant(
    val driverId: Int,
    val name: String,
    val teamId: Int,
    val raceNumber: Int,
    val nationality: Int,
    /** Relative pace: 1.0 = fastest car, lower = slower. */
    val pace: Float,
    val livery: Int,
)

/**
 * The 20-car 2025 grid for format 2025 (leaving slots 20-21 empty, as a real race does), and the
 * 22-car, 11-team 2026 grid with '26 team ids for format 2026.
 */
object MockRoster {
    fun forFormat(format: PacketFormat): List<MockEntrant> =
        if (format == PacketFormat.F1_25_SEASON_2026) grid2026 else grid2025

    private fun e(driverId: Int, name: String, team: Int, num: Int, nat: Int, pace: Float, livery: Int) =
        MockEntrant(driverId, name, team, num, nat, pace, livery)

    private const val MERCEDES = 0x27F4D2
    private const val FERRARI = 0xE8002D
    private const val RED_BULL = 0x3671C6
    private const val WILLIAMS = 0x64C4FF
    private const val ASTON = 0x229971
    private const val ALPINE = 0x0093CC
    private const val RB = 0x6692FF
    private const val HAAS = 0xB6BABD
    private const val MCLAREN = 0xFF8000
    private const val SAUBER = 0x52E252
    private const val AUDI = 0xBB0A30
    private const val CADILLAC = 0xC8C8C8

    // Nationality ids: 2 Argentinean, 3 Australian, 9 Brazilian, 10 British, 13 Canadian,
    // 22 Dutch, 27 Finnish, 28 French, 29 German, 41 Italian, 43 Japanese, 52 Mexican,
    // 53 Monegasque, 54 New Zealander, 77 Spanish, 80 Thai.
    private val grid2025 = listOf(
        e(54, "NORRIS", 8, 4, 10, 1.000f, MCLAREN),
        e(112, "PIASTRI", 8, 81, 3, 0.999f, MCLAREN),
        e(9, "VERSTAPPEN", 2, 1, 22, 0.998f, RED_BULL),
        e(50, "RUSSELL", 0, 63, 10, 0.996f, MERCEDES),
        e(58, "LECLERC", 1, 16, 53, 0.996f, FERRARI),
        e(7, "HAMILTON", 1, 44, 10, 0.994f, FERRARI),
        e(165, "ANTONELLI", 0, 12, 41, 0.993f, MERCEDES),
        e(62, "ALBON", 3, 23, 80, 0.990f, WILLIAMS),
        e(0, "SAINZ", 3, 55, 77, 0.989f, WILLIAMS),
        e(3, "ALONSO", 4, 14, 77, 0.988f, ASTON),
        e(149, "HADJAR", 6, 6, 28, 0.988f, RB),
        e(94, "TSUNODA", 2, 22, 43, 0.987f, RED_BULL),
        e(10, "HULKENBERG", 9, 27, 29, 0.986f, SAUBER),
        e(113, "LAWSON", 6, 30, 54, 0.986f, RB),
        e(59, "GASLY", 5, 10, 28, 0.985f, ALPINE),
        e(17, "OCON", 7, 31, 28, 0.985f, HAAS),
        e(147, "BEARMAN", 7, 87, 10, 0.984f, HAAS),
        e(19, "STROLL", 4, 18, 13, 0.983f, ASTON),
        e(161, "BORTOLETO", 9, 5, 9, 0.982f, SAUBER),
        e(162, "COLAPINTO", 5, 43, 2, 0.980f, ALPINE),
    )

    private val grid2026 = listOf(
        e(54, "NORRIS", 484, 1, 10, 1.000f, MCLAREN),
        e(112, "PIASTRI", 484, 81, 3, 0.999f, MCLAREN),
        e(50, "RUSSELL", 476, 63, 10, 0.999f, MERCEDES),
        e(9, "VERSTAPPEN", 478, 3, 22, 0.998f, RED_BULL),
        e(165, "ANTONELLI", 476, 12, 41, 0.996f, MERCEDES),
        e(58, "LECLERC", 477, 16, 53, 0.995f, FERRARI),
        e(7, "HAMILTON", 477, 44, 10, 0.994f, FERRARI),
        e(149, "HADJAR", 478, 6, 28, 0.992f, RED_BULL),
        e(0, "SAINZ", 479, 55, 77, 0.990f, WILLIAMS),
        e(62, "ALBON", 479, 23, 80, 0.989f, WILLIAMS),
        e(3, "ALONSO", 480, 14, 77, 0.988f, ASTON),
        e(19, "STROLL", 480, 18, 13, 0.984f, ASTON),
        e(59, "GASLY", 481, 10, 28, 0.987f, ALPINE),
        e(162, "COLAPINTO", 481, 43, 2, 0.983f, ALPINE),
        e(113, "LAWSON", 482, 30, 54, 0.986f, RB),
        e(188, "LINDBLAD", 482, 41, 10, 0.984f, RB),
        e(17, "OCON", 483, 31, 28, 0.985f, HAAS),
        e(147, "BEARMAN", 483, 87, 10, 0.985f, HAAS),
        e(10, "HULKENBERG", 485, 27, 29, 0.986f, AUDI),
        e(161, "BORTOLETO", 485, 5, 9, 0.985f, AUDI),
        e(15, "BOTTAS", 486, 77, 27, 0.980f, CADILLAC),
        e(14, "PEREZ", 486, 11, 52, 0.980f, CADILLAC),
    )
}
