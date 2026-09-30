package com.dashwroom.f1telemetry.core.spec

/** Visual tyre compounds as sent in `m_visualTyreCompound` (CarStatus / tyre history). */
enum class VisualCompound(val id: Int, val shortLabel: String) {
    SOFT(16, "S"),
    MEDIUM(17, "M"),
    HARD(18, "H"),
    INTER(7, "I"),
    WET(8, "W"),
    CLASSIC_DRY(9, "D"),
    CLASSIC_WET(10, "W"),
    F2_SUPER_SOFT(19, "SS"),
    F2_SOFT(20, "S"),
    F2_MEDIUM(21, "M"),
    F2_HARD(22, "H"),
    F2_WET(15, "W"),
    UNKNOWN(-1, "?");

    companion object {
        // Note: 19..22 overlap F1 *actual* ids but are visual ids only in F2 ('20 era) data.
        fun fromId(id: Int): VisualCompound = entries.firstOrNull { it.id == id } ?: UNKNOWN
    }
}

/** Actual compounds: F1 Modern C0..C6, inter/wet; F1 Classic dry/wet; F2 compounds. */
object ActualCompound {
    fun name(id: Int): String = when (id) {
        16 -> "C5"; 17 -> "C4"; 18 -> "C3"; 19 -> "C2"; 20 -> "C1"; 21 -> "C0"; 22 -> "C6"
        7 -> "Inter"; 8 -> "Wet"; 9 -> "Dry (classic)"; 10 -> "Wet (classic)"
        11 -> "Super soft (F2)"; 12 -> "Soft (F2)"; 13 -> "Medium (F2)"; 14 -> "Hard (F2)"; 15 -> "Wet (F2)"
        else -> "Unknown ($id)"
    }
}
