package com.dashwroom.f1telemetry.ui.adaptive

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import kotlin.math.roundToInt

/** How a list-detail screen arranges itself for the current window. */
enum class PaneMode {
    /** One pane; the detail opens in a bottom sheet. */
    SINGLE,

    /** List and detail side by side (expanded width, or a book-posture foldable). */
    SIDE_BY_SIDE,
}

/** A separating fold / hinge, in window pixels. */
@Immutable
data class Hinge(val bounds: Rect, val vertical: Boolean)

@Immutable
data class PaneLayoutInfo(val mode: PaneMode, val hinge: Hinge?)

/**
 * Picks the pane mode from the window size class and folding features (Jetpack WindowManager's
 * FoldingFeature, surfaced through `currentWindowAdaptiveInfo().windowPosture`):
 * - expanded width (≥ 840 dp) → side by side;
 * - a separating vertical hinge (book posture / unfolded with a physical gap) → side by side,
 *   split exactly at the hinge;
 * - otherwise a single pane.
 */
@Composable
fun rememberPaneLayout(): PaneLayoutInfo {
    val info = currentWindowAdaptiveInfo()
    val hinge = info.windowPosture.hingeList.firstOrNull { it.isSeparating }?.let {
        Hinge(Rect(it.bounds.left, it.bounds.top, it.bounds.right, it.bounds.bottom), it.isVertical)
    }
    val expanded = info.windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)
    val mode = if (expanded || hinge?.vertical == true) PaneMode.SIDE_BY_SIDE else PaneMode.SINGLE
    return remember(mode, hinge) { PaneLayoutInfo(mode, hinge) }
}

/**
 * Two panes in a row, split by [firstWeight] of the width — or, when [hinge] is vertical and
 * crosses this layout, at the hinge itself so no content lands in the fold. A horizontal hinge
 * (tabletop posture) stacks the panes above and below it instead. No fixed widths anywhere.
 */
@Composable
fun TwoPane(
    first: @Composable () -> Unit,
    second: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    firstWeight: Float = 0.55f,
    gap: Dp = 12.dp,
    hinge: Hinge? = null,
) {
    var windowBounds by remember { mutableStateOf(Rect.Zero) }
    Layout(
        contents = listOf({ Box { first() } }, { Box { second() } }),
        modifier = modifier.onGloballyPositioned { windowBounds = it.boundsInWindow() },
    ) { (firstMeasurables, secondMeasurables), constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val gapPx = gap.roundToPx()
        val local = hinge?.let { h ->
            // The hinge in this layout's coordinates, if it actually crosses it.
            val r = h.bounds.translate(-windowBounds.left, -windowBounds.top)
            if (h.vertical && r.left > 0 && r.right < width) r
            else if (!h.vertical && r.top > 0 && r.bottom < height) r
            else null
        }
        if (local != null && hinge?.vertical == false) {
            val topH = local.top.roundToInt()
            val bottomY = local.bottom.roundToInt()
            val a = firstMeasurables.first().measure(Constraints.fixed(width, topH))
            val b = secondMeasurables.first().measure(Constraints.fixed(width, (height - bottomY).coerceAtLeast(0)))
            layout(width, height) {
                a.place(0, 0)
                b.place(0, bottomY)
            }
        } else {
            val firstW: Int
            val secondX: Int
            if (local != null) {
                firstW = local.left.roundToInt()
                secondX = local.right.roundToInt()
            } else {
                firstW = ((width - gapPx) * firstWeight).roundToInt()
                secondX = firstW + gapPx
            }
            val a = firstMeasurables.first().measure(Constraints.fixed(firstW.coerceAtLeast(0), height))
            val b = secondMeasurables.first().measure(Constraints.fixed((width - secondX).coerceAtLeast(0), height))
            layout(width, height) {
                a.place(0, 0)
                b.place(secondX, 0)
            }
        }
    }
}
