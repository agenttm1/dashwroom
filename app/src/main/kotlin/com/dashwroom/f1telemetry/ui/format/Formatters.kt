package com.dashwroom.f1telemetry.ui.format

import com.dashwroom.f1telemetry.core.model.DriverState
import java.util.Locale
import kotlin.math.abs

/** Timing formats used across screens. All tolerate "no data" (≤ 0) and return an em dash. */
object Fmt {
    const val NONE = "—"

    fun lapTime(ms: Long): String {
        if (ms <= 0) return NONE
        return String.format(Locale.US, "%d:%02d.%03d", ms / 60_000, ms / 1000 % 60, ms % 1000)
    }

    fun sector(ms: Int): String {
        if (ms <= 0) return NONE
        return if (ms >= 60_000) lapTime(ms.toLong()) else String.format(Locale.US, "%d.%03d", ms / 1000, ms % 1000)
    }

    /** "+1.234", "+1:02.345". */
    fun gap(ms: Int): String {
        if (ms <= 0) return NONE
        return if (ms >= 60_000) "+" + lapTime(ms.toLong()) else String.format(Locale.US, "+%d.%03d", ms / 1000, ms % 1000)
    }

    /** Signed delta in seconds with three decimals: "+0.284", "−0.105". */
    fun delta(ms: Int): String {
        val sign = if (ms < 0) "−" else "+"
        val a = abs(ms)
        return String.format(Locale.US, "%s%d.%03d", sign, a / 1000, a % 1000)
    }

    fun interval(d: DriverState, leader: Boolean): String = when {
        leader -> "Leader"
        d.lapsBehindLeader > 0 && d.position > 1 && d.intervalMs == 0 -> "+${d.lapsBehindLeader} L"
        else -> gap(d.intervalMs)
    }

    fun toLeader(d: DriverState): String = when {
        d.position == 1 -> "Leader"
        d.lapsBehindLeader > 0 -> "+${d.lapsBehindLeader} LAP" + if (d.lapsBehindLeader > 1) "S" else ""
        else -> gap(d.gapToLeaderMs)
    }

    /** "12:34" or "1:02:03". */
    fun clock(seconds: Int): String {
        if (seconds < 0) return NONE
        val h = seconds / 3600
        val m = seconds / 60 % 60
        val s = seconds % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%d:%02d", m, s)
    }

    fun megajoules(joules: Float): String = String.format(Locale.US, "%.2f MJ", joules / 1_000_000f)

    fun oneDecimal(v: Float): String = String.format(Locale.US, "%.1f", v)

    fun signedOneDecimal(v: Float): String = String.format(Locale.US, "%+.1f", v)
}
