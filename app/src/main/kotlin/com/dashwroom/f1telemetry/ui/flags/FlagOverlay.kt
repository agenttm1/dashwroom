package com.dashwroom.f1telemetry.ui.flags

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dashwroom.f1telemetry.core.model.FlagState

/**
 * Race-control alerts drawn over every screen:
 * - a flash (colour wash + big card) when a yellow, red or green flag or a safety car starts;
 * - a pulsing glow around the screen edges for as long as a yellow or red flag stays out;
 * - a small blue-flag badge in the top corner while a faster car is lapping you.
 *
 * Pulses run at well under 3 per second (photosensitivity guidance). Nothing here takes touch
 * input, so the screen underneath stays fully usable.
 */
@Composable
fun FlagOverlay(flags: FlagState, flashes: Boolean, modifier: Modifier = Modifier) {
    var previous by remember { mutableStateOf(flags) }
    var alert by remember { mutableStateOf<FlagAlert?>(null) }
    var serial by remember { mutableIntStateOf(0) }
    LaunchedEffect(flags) {
        val next = FlagAlertLogic.alertFor(previous, flags)
        previous = flags
        if (next != null && flashes) {
            alert = next
            serial++
        }
    }
    val wash = remember { Animatable(0f) }
    LaunchedEffect(serial) {
        val a = alert ?: return@LaunchedEffect
        repeat(a.pulses) {
            wash.animateTo(1f, tween(durationMillis = 180))
            wash.animateTo(0.2f, tween(durationMillis = 480))
        }
        wash.animateTo(0f, tween(durationMillis = 400))
        alert = null
    }
    val glow = if (flashes) FlagAlertLogic.edgeGlow(flags) else null

    Box(modifier.fillMaxSize()) {
        if (glow != null) EdgeGlowLayer(glow.color)
        val washColor = alert?.color ?: Color.Transparent
        Box(
            Modifier
                .fillMaxSize()
                .clearAndSetSemantics { }
                .drawBehind {
                    val v = wash.value // draw-phase read only
                    if (v > 0f) drawRect(washColor.copy(alpha = 0.45f * v))
                },
        )
        AnimatedVisibility(
            visible = alert != null,
            enter = scaleIn(initialScale = 0.85f) + fadeIn(),
            exit = scaleOut(targetScale = 0.95f) + fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            val shown = remember { mutableStateOf(alert) }
            alert?.let { shown.value = it }
            shown.value?.let { AlertCard(it) }
        }
        AnimatedVisibility(
            visible = FlagAlertLogic.showBlue(flags),
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(top = 64.dp, end = 12.dp),
        ) { BlueFlagBadge() }
    }
}

@Composable
private fun AlertCard(alert: FlagAlert) {
    Surface(
        color = alert.color,
        contentColor = alert.textColor,
        shape = RoundedCornerShape(28.dp),
        shadowElevation = 24.dp,
        modifier = Modifier
            .padding(24.dp)
            .widthIn(max = 620.dp)
            .fillMaxWidth()
            .clearAndSetSemantics {
                liveRegion = LiveRegionMode.Assertive
                contentDescription = "${alert.title}. ${alert.subtitle}"
            },
    ) {
        Row(Modifier.padding(horizontal = 28.dp, vertical = 22.dp), verticalAlignment = Alignment.CenterVertically) {
            FlagGlyph(alert.textColor, Modifier.size(72.dp), cloth = alert.textColor.copy(alpha = 0.18f))
            Column(Modifier.padding(start = 20.dp)) {
                Text(alert.title, fontSize = 40.sp, fontWeight = FontWeight.Black, lineHeight = 42.sp, letterSpacing = 1.sp)
                Text(alert.subtitle, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun BlueFlagBadge() {
    val pulse by rememberInfiniteTransition(label = "blue").animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse",
    )
    Surface(
        color = FlagColors.Blue,
        contentColor = Color.White,
        shape = RoundedCornerShape(14.dp),
        shadowElevation = 10.dp,
        modifier = Modifier.clearAndSetSemantics {
            liveRegion = LiveRegionMode.Polite
            contentDescription = "Blue flag: let the faster car through"
        },
    ) {
        Row(
            Modifier
                .drawBehind {
                    // Glowing rim, alpha animated in the draw phase.
                    drawRoundRect(
                        Color.White.copy(alpha = 0.6f * pulse),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(14.dp.toPx()),
                        style = Stroke(2.dp.toPx()),
                    )
                }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FlagGlyph(Color.White, Modifier.size(28.dp), cloth = Color.White.copy(alpha = 0.25f))
            Column(Modifier.padding(start = 10.dp)) {
                Text("BLUE FLAG", fontSize = 15.sp, fontWeight = FontWeight.Black, letterSpacing = 0.8.sp)
                Text("Let the faster car by", fontSize = 12.sp, textAlign = TextAlign.Start)
            }
        }
    }
}

/** Pulsing colour gradient along all four edges. */
@Composable
private fun EdgeGlowLayer(color: Color, width: Dp = 40.dp) {
    val pulse by rememberInfiniteTransition(label = "glow").animateFloat(
        initialValue = 0.35f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(650, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse",
    )
    Box(
        Modifier
            .fillMaxSize()
            .clearAndSetSemantics { }
            .drawBehind {
                val a = pulse
                val w = width.toPx()
                val c = color.copy(alpha = a)
                val clear = color.copy(alpha = 0f)
                drawRect(Brush.verticalGradient(listOf(c, clear), 0f, w), size = Size(size.width, w))
                drawRect(Brush.verticalGradient(listOf(clear, c), size.height - w, size.height), Offset(0f, size.height - w), Size(size.width, w))
                drawRect(Brush.horizontalGradient(listOf(c, clear), 0f, w), size = Size(w, size.height))
                drawRect(Brush.horizontalGradient(listOf(clear, c), size.width - w, size.width), Offset(size.width - w, 0f), Size(w, size.height))
            },
    )
}

/** A small waving flag on a pole. */
@Composable
fun FlagGlyph(color: Color, modifier: Modifier = Modifier, cloth: Color = color.copy(alpha = 0.2f)) {
    Canvas(modifier) { drawFlag(color, cloth) }
}

internal fun DrawScope.drawFlag(color: Color, cloth: Color) {
    val w = size.width
    val h = size.height
    val pole = w * 0.08f
    drawRect(color, Offset(w * 0.12f, h * 0.06f), Size(pole, h * 0.9f))
    val left = w * 0.12f + pole
    val path = Path().apply {
        moveTo(left, h * 0.1f)
        cubicTo(left + w * 0.25f, h * 0.0f, left + w * 0.45f, h * 0.22f, w * 0.94f, h * 0.1f)
        lineTo(w * 0.94f, h * 0.55f)
        cubicTo(left + w * 0.45f, h * 0.67f, left + w * 0.25f, h * 0.45f, left, h * 0.55f)
        close()
    }
    drawPath(path, cloth)
    drawPath(path, color, style = Stroke(w * 0.06f))
}
