package com.dashwroom.f1telemetry.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dashwroom.f1telemetry.core.spec.VisualCompound
import com.dashwroom.f1telemetry.ui.theme.DashColors
import com.dashwroom.f1telemetry.ui.theme.DashTheme

fun DashColors.tyre(visual: Int): Color = when (VisualCompound.fromId(visual)) {
    VisualCompound.SOFT, VisualCompound.F2_SUPER_SOFT, VisualCompound.F2_SOFT -> tyreSoft
    VisualCompound.MEDIUM, VisualCompound.F2_MEDIUM -> tyreMedium
    VisualCompound.HARD, VisualCompound.F2_HARD, VisualCompound.CLASSIC_DRY -> tyreHard
    VisualCompound.INTER -> tyreInter
    VisualCompound.WET, VisualCompound.CLASSIC_WET, VisualCompound.F2_WET -> tyreWet
    VisualCompound.UNKNOWN -> Color.Gray
}

fun compoundName(visual: Int): String = when (VisualCompound.fromId(visual)) {
    VisualCompound.SOFT, VisualCompound.F2_SOFT -> "Soft"
    VisualCompound.F2_SUPER_SOFT -> "Super soft"
    VisualCompound.MEDIUM, VisualCompound.F2_MEDIUM -> "Medium"
    VisualCompound.HARD, VisualCompound.F2_HARD -> "Hard"
    VisualCompound.INTER -> "Intermediate"
    VisualCompound.WET, VisualCompound.CLASSIC_WET, VisualCompound.F2_WET -> "Wet"
    VisualCompound.CLASSIC_DRY -> "Dry"
    VisualCompound.UNKNOWN -> "Unknown"
}

/** Pirelli-style ring with the compound letter. */
@Composable
fun TyreBadge(visual: Int, modifier: Modifier = Modifier, size: Dp = 22.dp) {
    val color = DashTheme.colors.tyre(visual)
    val label = VisualCompound.fromId(visual).shortLabel
    Box(
        modifier.size(size).semantics { contentDescription = "${compoundName(visual)} tyres" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size)) {
            val stroke = this.size.minDimension * 0.16f
            drawCircle(Color.Black.copy(alpha = 0.55f))
            drawCircle(color, radius = (this.size.minDimension - stroke) / 2f, style = Stroke(stroke))
        }
        Text(
            label,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = (size.value * 0.45f).sp,
            fontWeight = FontWeight.Bold,
            // Tight, centred line box so the letter sits in the middle of the ring at any size.
            style = TextStyle(
                lineHeight = (size.value * 0.45f).sp,
                lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
            ),
        )
    }
}
