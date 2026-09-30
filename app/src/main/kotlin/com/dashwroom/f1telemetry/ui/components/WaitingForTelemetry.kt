package com.dashwroom.f1telemetry.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Empty state shared by every telemetry screen: a shimmering skeleton hinting at the layout to
 * come, with a message card on top — never a wall of zeros.
 */
@Composable
fun WaitingForTelemetry(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Box(modifier.fillMaxSize()) {
        SkeletonGrid(Modifier.fillMaxSize().padding(16.dp))
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            shadowElevation = 8.dp,
            modifier = Modifier.align(Alignment.Center).padding(24.dp).widthIn(max = 460.dp),
        ) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                if (actionLabel != null && onAction != null) {
                    Spacer(Modifier.height(16.dp))
                    FilledTonalButton(onClick = onAction) { Text(actionLabel) }
                }
            }
        }
    }
}

/** Card-shaped placeholders; column count follows the available width, never a fixed size. */
@Composable
private fun SkeletonGrid(modifier: Modifier) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val shift by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1_600, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmer",
    )
    val base = MaterialTheme.colorScheme.surfaceContainer
    val highlight = MaterialTheme.colorScheme.surfaceContainerHighest
    BoxWithConstraints(modifier.clearAndSetSemantics { }) {
        val columns = when {
            maxWidth >= 840.dp -> 3
            maxWidth >= 600.dp -> 2
            else -> 1
        }
        val rows = (maxHeight / 150.dp).toInt().coerceAtLeast(1)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(rows) { r ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    repeat(columns) { c ->
                        Box(
                            Modifier
                                .weight(if ((r + c) % 3 == 0) 1.4f else 1f)
                                .height(if (r == 0) 96.dp else 138.dp)
                                .drawWithCache {
                                    // Shimmer is a draw-phase-only read of `shift`: no recomposition.
                                    onDrawBehind {
                                        val x = size.width * shift
                                        drawRoundRect(
                                            brush = Brush.linearGradient(
                                                listOf(base, highlight, base),
                                                start = Offset(x - size.width, 0f),
                                                end = Offset(x, size.height),
                                            ),
                                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx()),
                                        )
                                    }
                                },
                        )
                    }
                }
            }
        }
    }
}

/** Simple rounded block used by screen-specific skeletons. */
@Composable
fun SkeletonBlock(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.surfaceContainer) {
    Box(modifier.fillMaxWidth().background(color, RoundedCornerShape(12.dp)))
}
