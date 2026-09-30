package com.dashwroom.f1telemetry.ui.hot

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import kotlin.math.abs

/**
 * Allocation-free text for values that change every frame (speed, gear, lap time, delta).
 * Each glyph is measured once; drawing lays pre-measured glyphs at fixed (tabular) advances,
 * so no String, TextLayoutResult or Paragraph is created per frame.
 */
class GlyphAtlas(measurer: TextMeasurer, style: TextStyle, glyphs: String = DEFAULT_GLYPHS) {
    private val layouts = arrayOfNulls<TextLayoutResult>(128)
    val advance: Float
    val height: Float

    init {
        var maxWidth = 0f
        var maxHeight = 0f
        for (ch in glyphs) {
            val r = measurer.measure(ch.toString(), style)
            val slot = slot(ch)
            if (slot >= 0) layouts[slot] = r
            if (ch.isDigit()) maxWidth = maxOf(maxWidth, r.size.width.toFloat())
            maxHeight = maxOf(maxHeight, r.size.height.toFloat())
        }
        advance = maxWidth
        height = maxHeight
    }

    fun width(buffer: GlyphBuffer): Float {
        var w = 0f
        for (i in 0 until buffer.length) w += advanceOf(buffer.chars[i])
        return w
    }

    private fun advanceOf(c: Char): Float = when (c) {
        '.', ':', ' ' -> advance * 0.45f
        else -> advance
    }

    /** Draws [buffer] with its left edge at [x]; [y] is the top. */
    fun draw(scope: DrawScope, buffer: GlyphBuffer, x: Float, y: Float, color: Color) {
        var cx = x
        for (i in 0 until buffer.length) {
            val c = buffer.chars[i]
            val w = advanceOf(c)
            val slot = slot(c)
            val layout = if (slot >= 0) layouts[slot] else null
            if (layout != null) {
                // Centre narrow glyphs inside their tabular cell.
                val dx = (w - layout.size.width) / 2f
                scope.drawText(layout, color = color, topLeft = Offset(cx + dx, y))
            }
            cx += w
        }
    }

    fun drawRightAligned(scope: DrawScope, buffer: GlyphBuffer, right: Float, y: Float, color: Color) =
        draw(scope, buffer, right - width(buffer), y, color)

    fun drawCentered(scope: DrawScope, buffer: GlyphBuffer, centerX: Float, y: Float, color: Color) =
        draw(scope, buffer, centerX - width(buffer) / 2f, y, color)

    companion object {
        /** ASCII maps to itself; the typographic minus takes the unused DEL slot. */
        private fun slot(c: Char): Int = when {
            c.code < 127 -> c.code
            c == '−' -> 127
            else -> -1
        }

        const val DEFAULT_GLYPHS = "0123456789:.+-−NR "
    }
}

@Composable
fun rememberGlyphAtlas(style: TextStyle, glyphs: String = GlyphAtlas.DEFAULT_GLYPHS): GlyphAtlas {
    val measurer = rememberTextMeasurer(cacheSize = 0)
    return remember(style, glyphs, measurer) { GlyphAtlas(measurer, style, glyphs) }
}

/** Reusable char buffer with allocation-free number formatting. */
class GlyphBuffer(capacity: Int = 16) {
    val chars = CharArray(capacity)
    var length = 0
        private set

    fun clear(): GlyphBuffer {
        length = 0
        return this
    }

    fun append(c: Char): GlyphBuffer {
        if (length < chars.size) chars[length++] = c
        return this
    }

    fun appendInt(value: Int, minDigits: Int = 1): GlyphBuffer {
        var v = value
        if (v < 0) {
            append('-')
            v = -v
        }
        val start = length
        var digits = 0
        do {
            append('0' + v % 10)
            v /= 10
            digits++
        } while (v > 0 || digits < minDigits)
        chars.reverse(start, length)
        return this
    }

    /** m:ss.fff */
    fun appendLapTime(ms: Long): GlyphBuffer {
        if (ms <= 0) return append('-').append(':').append('-').append('-').append('.').append('-').append('-').append('-')
        appendInt((ms / 60_000).toInt())
        append(':')
        appendInt((ms / 1000 % 60).toInt(), 2)
        append('.')
        return appendInt((ms % 1000).toInt(), 3)
    }

    /** +s.fff / −s.fff */
    fun appendDelta(ms: Int): GlyphBuffer {
        append(if (ms < 0) '−' else '+')
        val a = abs(ms)
        appendInt(a / 1000)
        append('.')
        return appendInt(a % 1000, 3)
    }
}
