package com.dashwroom.f1telemetry.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.dashwroom.f1telemetry.core.model.DamageState
import com.dashwroom.f1telemetry.core.model.PlayerCarState
import com.dashwroom.f1telemetry.core.model.WheelState
import kotlin.math.min

/** Fill colour per car part; wheels in the spec's order RL, RR, FL, FR. */
@Immutable
data class CarPartColors(
    val frontLeftWing: Color,
    val frontRightWing: Color,
    val body: Color,
    val floor: Color,
    val diffuser: Color,
    val rearWing: Color,
    val wheels: List<Color>,
) {
    companion object {
        fun fromDamage(d: DamageState, wheels: List<WheelState>, fallback: Color): CarPartColors = CarPartColors(
            frontLeftWing = Palette.damage(d.frontLeftWing.toFloat()),
            frontRightWing = Palette.damage(d.frontRightWing.toFloat()),
            body = Palette.damage(maxOf(d.sidepod, d.engine).toFloat()),
            floor = Palette.damage(d.floor.toFloat()),
            diffuser = Palette.damage(d.diffuser.toFloat()),
            rearWing = Palette.damage(d.rearWing.toFloat()),
            wheels = List(4) { i -> wheels.getOrNull(i)?.let { Palette.damage(maxOf(it.wearPercent, it.damagePercent.toFloat())) } ?: fallback },
        )
    }
}

/**
 * Top-down car silhouette in a 100 × 220 design space, scaled to fit. [wheelOverlay] can paint
 * each wheel itself (the tyre heat map) given its rectangle and index (RL, RR, FL, FR).
 */
@Composable
fun CarDiagram(
    parts: CarPartColors,
    modifier: Modifier = Modifier,
    description: String = "Car damage diagram",
    wheelOverlay: (DrawScope.(wheel: Int, rect: Rect) -> Unit)? = null,
) {
    val outline = MaterialTheme.colorScheme.outline
    Canvas(modifier.semantics { contentDescription = description }) {
        val s = min(size.width / 100f, size.height / 220f)
        val ox = (size.width - 100f * s) / 2f
        val oy = (size.height - 220f * s) / 2f
        fun r(x: Float, y: Float, w: Float, h: Float) = Rect(Offset(ox + x * s, oy + y * s), Size(w * s, h * s))
        val corner = CornerRadius(3f * s)
        val stroke = Stroke(1.2f * s)

        // Front wing halves.
        part(r(4f, 4f, 45f, 12f), parts.frontLeftWing, outline, corner, stroke)
        part(r(51f, 4f, 45f, 12f), parts.frontRightWing, outline, corner, stroke)
        // Floor (under everything else): a wide plank between the axles.
        part(r(20f, 70f, 60f, 110f), parts.floor, outline, CornerRadius(8f * s), stroke)
        // Body: nose + cockpit + sidepods as one path.
        val body = Path().apply {
            moveTo(ox + 46f * s, oy + 16f * s)
            lineTo(ox + 54f * s, oy + 16f * s)
            lineTo(ox + 58f * s, oy + 70f * s)
            lineTo(ox + 74f * s, oy + 90f * s)
            lineTo(ox + 72f * s, oy + 160f * s)
            lineTo(ox + 60f * s, oy + 190f * s)
            lineTo(ox + 40f * s, oy + 190f * s)
            lineTo(ox + 28f * s, oy + 160f * s)
            lineTo(ox + 26f * s, oy + 90f * s)
            lineTo(ox + 42f * s, oy + 70f * s)
            close()
        }
        drawPath(body, parts.body.copy(alpha = 0.9f))
        drawPath(body, outline, style = stroke)
        // Cockpit opening.
        drawOval(Color.Black.copy(alpha = 0.55f), topLeft = Offset(ox + 44f * s, oy + 92f * s), size = Size(12f * s, 26f * s))
        // Diffuser and rear wing.
        part(r(32f, 190f, 36f, 10f), parts.diffuser, outline, corner, stroke)
        part(r(14f, 202f, 72f, 14f), parts.rearWing, outline, corner, stroke)
        // Wheels: RL, RR, FL, FR.
        val wheelRects = arrayOf(r(2f, 150f, 22f, 40f), r(76f, 150f, 22f, 40f), r(4f, 30f, 20f, 36f), r(76f, 30f, 20f, 36f))
        for (i in 0 until 4) {
            val rect = wheelRects[i]
            if (wheelOverlay != null) {
                wheelOverlay(i, rect)
            } else {
                part(rect, parts.wheels.getOrElse(i) { outline }, outline, CornerRadius(5f * s), stroke)
            }
        }
    }
}

private fun DrawScope.part(rect: Rect, fill: Color, outline: Color, corner: CornerRadius, stroke: Stroke) {
    drawRoundRect(fill.copy(alpha = 0.85f), topLeft = rect.topLeft, size = rect.size, cornerRadius = corner)
    drawRoundRect(outline, topLeft = rect.topLeft, size = rect.size, cornerRadius = corner, style = stroke)
}

/** Labels for the four wheels in spec order. */
val WheelNames = listOf("Rear left", "Rear right", "Front left", "Front right")
val WheelShort = listOf("RL", "RR", "FL", "FR")

/** Display order for 2 × 2 grids: FL, FR, RL, RR. */
val WheelGridOrder = intArrayOf(PlayerCarState.FRONT_LEFT, PlayerCarState.FRONT_RIGHT, PlayerCarState.REAR_LEFT, PlayerCarState.REAR_RIGHT)
