package com.dashwroom.f1telemetry.ui.flags

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dashwroom.f1telemetry.core.model.FlagState
import com.dashwroom.f1telemetry.core.model.SafetyCarMode

/** What the persistent banner announces, most serious first. */
enum class BannerKind(val badge: String, val title: String, val accent: Color, val stripe: Color) {
    RED_FLAG("RED", "RED FLAG", FlagColors.Red, Color.White),
    SAFETY_CAR("SC", "SAFETY CAR", FlagColors.Yellow, Color.Black),
    VIRTUAL_SAFETY_CAR("VSC", "VIRTUAL SAFETY CAR", FlagColors.Yellow, Color.Black),
    FORMATION_LAP("F", "FORMATION LAP", Color(0xFFB0B8C4), Color.Black),
    ;

    companion object {
        fun of(f: FlagState): BannerKind? = when {
            f.redFlag -> RED_FLAG
            f.safetyCar == SafetyCarMode.FULL -> SAFETY_CAR
            f.safetyCar == SafetyCarMode.VIRTUAL -> VIRTUAL_SAFETY_CAR
            f.safetyCar == SafetyCarMode.FORMATION_LAP -> FORMATION_LAP
            else -> null
        }

        fun subtitle(kind: BannerKind, f: FlagState): String = when (kind) {
            RED_FLAG -> "Session suspended · return to the pit lane"
            SAFETY_CAR -> if (f.safetyCarEnding) "Safety car in this lap · get ready for the restart" else "No overtaking · DRS disabled · close up to the queue"
            VIRTUAL_SAFETY_CAR -> if (f.safetyCarEnding) "VSC ending · racing resumes on the green" else "Keep above the delta time · no overtaking"
            FORMATION_LAP -> "Keep your grid position · warm the tyres"
        }
    }
}

/**
 * Persistent race-control strip shown at the top of every screen while a safety car, virtual
 * safety car, formation lap or red flag is active — styled like a broadcast graphic, with animated
 * hazard stripes. It sits in the layout (pushing content down) rather than covering it.
 */
@Composable
fun RaceControlBanner(flags: FlagState, modifier: Modifier = Modifier) {
    val kind = BannerKind.of(flags)
    // Keep the last content while the exit animation runs.
    val last = remember { mutableStateOf<Pair<BannerKind, String>?>(null) }
    if (kind != null) last.value = kind to BannerKind.subtitle(kind, flags)
    AnimatedVisibility(
        visible = kind != null,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier,
    ) {
        last.value?.let { (k, subtitle) -> Banner(k, subtitle, ending = flags.safetyCarEnding && k != BannerKind.RED_FLAG) }
    }
}

@Composable
private fun Banner(kind: BannerKind, subtitle: String, ending: Boolean) {
    val transition = rememberInfiniteTransition(label = "banner")
    val stripeShift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_200, easing = LinearEasing)),
        label = "stripes",
    )
    val endingPulse by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600, easing = LinearEasing), RepeatMode.Reverse),
        label = "ending",
    )
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .height(58.dp)
            .clip(shape)
            .background(Color(0xFF0F1114))
            .border(BorderStroke(1.5.dp, kind.accent), shape)
            .clearAndSetSemantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = "${kind.title}. $subtitle"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Hazard-striped block with the badge, stripes scrolling in the draw phase.
        Box(
            Modifier
                .width(88.dp)
                .fillMaxHeight()
                .drawWithCache {
                    // Stripes built once per size; the animation only translates them.
                    val step = 18.dp.toPx()
                    val stripeW = step / 2f
                    val stripes = Path()
                    var x = -size.height - step
                    while (x < size.width + step) {
                        stripes.moveTo(x, size.height)
                        stripes.lineTo(x + stripeW, size.height)
                        stripes.lineTo(x + stripeW + size.height, 0f)
                        stripes.lineTo(x + size.height, 0f)
                        stripes.close()
                        x += step
                    }
                    val stripeColor = kind.stripe.copy(alpha = 0.85f)
                    onDrawBehind {
                        drawRect(kind.accent)
                        clipRect { translate(left = stripeShift * step) { drawPath(stripes, stripeColor) } }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                kind.badge,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                color = kind.accent,
                modifier = Modifier
                    .background(Color(0xFF0F1114), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(kind.title, color = kind.accent, fontSize = 20.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp, maxLines = 1)
            Text(subtitle, color = Color(0xFFDCE0E6), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (ending) {
            Text(
                "IN THIS LAP",
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                color = Color.Black,
                modifier = Modifier
                    .padding(end = 12.dp)
                    .drawBehind {
                        drawRoundRect(
                            FlagColors.Green.copy(alpha = endingPulse),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()),
                        )
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

