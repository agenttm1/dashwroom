package com.dashwroom.f1telemetry.ui.adaptive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The one container difference between phone and tablet for list-detail screens (Race,
 * Qualifying, Car, Analysis): the same [list] and [detail] composables are shown side by side on
 * large / folded windows, and as list + modal bottom sheet on compact ones.
 *
 * [idleDetail] fills the second pane when nothing is selected (only used side by side).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListDetail(
    hasSelection: Boolean,
    onDismissDetail: () -> Unit,
    list: @Composable () -> Unit,
    detail: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    idleDetail: (@Composable () -> Unit)? = null,
    layout: PaneLayoutInfo = rememberPaneLayout(),
    listWeight: Float = 0.58f,
) {
    if (layout.mode == PaneMode.SIDE_BY_SIDE) {
        TwoPane(
            first = { Box(Modifier.fillMaxSize()) { list() } },
            second = {
                Box(Modifier.fillMaxSize()) {
                    if (hasSelection) detail() else idleDetail?.invoke()
                }
            },
            modifier = modifier.fillMaxSize(),
            firstWeight = listWeight,
            gap = 12.dp,
            hinge = layout.hinge,
        )
    } else {
        Box(modifier.fillMaxSize()) { list() }
        if (hasSelection) {
            ModalBottomSheet(
                onDismissRequest = onDismissDetail,
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
            ) {
                detail()
            }
        }
    }
}
