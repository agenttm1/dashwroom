package com.dashwroom.f1telemetry.ui.screens.race

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dashwroom.f1telemetry.core.state.HotTelemetry
import com.dashwroom.f1telemetry.ui.adaptive.ListDetail
import com.dashwroom.f1telemetry.ui.adaptive.PaneLayoutInfo
import com.dashwroom.f1telemetry.ui.adaptive.PaneMode
import com.dashwroom.f1telemetry.ui.adaptive.TwoPane
import com.dashwroom.f1telemetry.ui.adaptive.rememberPaneLayout
import com.dashwroom.f1telemetry.ui.components.WaitingForTelemetry
import com.dashwroom.f1telemetry.ui.format.LocalDisplayPrefs
import com.dashwroom.f1telemetry.ui.hot.RaceTopStrip
import com.dashwroom.f1telemetry.ui.hot.rememberHotFrame
import com.dashwroom.f1telemetry.ui.preview.PreviewData
import com.dashwroom.f1telemetry.ui.theme.DashwroomTheme

@Composable
fun RaceScreen(onOpenConnect: () -> Unit, viewModel: RaceViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val frame = rememberHotFrame(viewModel.hot)
    RaceContent(state, viewModel.hot, frame, viewModel::select, viewModel::clearSelection, onOpenConnect)
}

/**
 * Landscape-first race view: full-rate strip on top; timing tower below. Side by side (tablet,
 * unfolded foldable) the second pane shows the selected driver, or pit strategy + race control
 * when nobody is selected. On phones the tower, feed and strategy are tabs and a driver opens in
 * a bottom sheet — the same composables, only the container differs.
 */
@Composable
fun RaceContent(
    state: RaceUiState,
    hot: HotTelemetry,
    frame: State<Long>,
    onSelect: (Int) -> Unit,
    onClearSelection: () -> Unit,
    onOpenConnect: () -> Unit,
    modifier: Modifier = Modifier,
    layout: PaneLayoutInfo = rememberPaneLayout(),
) {
    // Fade from the waiting skeleton to the real content when data starts flowing.
    Crossfade(state.hasData, modifier, label = "data") { ready ->
        if (!ready) {
            WaitingForTelemetry(
                title = "Waiting for the grid",
                message = "The timing tower appears as soon as lap data arrives from F1 25.",
                actionLabel = "Connection setup",
                onAction = onOpenConnect,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            val selectedDriver = state.race.driver(state.selected)
            val hinge = layout.hinge
            if (hinge != null && !hinge.vertical) {
                // Tabletop posture: live strip, strategy and race control on the upright half,
                // the tower on the flat half; a driver still opens in a sheet.
                TwoPane(
                    first = {
                        Column(Modifier.fillMaxSize()) {
                            RaceTopStrip(hot, frame, LocalDisplayPrefs.current.deltaReference)
                            Row(Modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                PitHelperCard(state.pitAdvice, Modifier.weight(1f))
                                EventFeed(state.events, Modifier.weight(1f).fillMaxHeight())
                            }
                        }
                    },
                    second = {
                        ListDetail(
                            hasSelection = selectedDriver != null,
                            onDismissDetail = onClearSelection,
                            layout = PaneLayoutInfo(PaneMode.SINGLE, null),
                            list = { TimingTower(state.race.drivers, state.race.bests, state.selected, onSelect) },
                            detail = { selectedDriver?.let { DriverDetail(it, state.history, state.race.bests, state.info, state.race.player, onClose = null) } },
                        )
                    },
                    modifier = Modifier.fillMaxSize(),
                    hinge = hinge,
                )
                return@Crossfade
            }
            Column(Modifier.fillMaxSize()) {
                RaceTopStrip(hot, frame, LocalDisplayPrefs.current.deltaReference)
                ListDetail(
                    hasSelection = selectedDriver != null,
                    onDismissDetail = onClearSelection,
                    layout = layout,
                    list = {
                        if (layout.mode == PaneMode.SIDE_BY_SIDE) {
                            TimingTower(state.race.drivers, state.race.bests, state.selected, onSelect)
                        } else {
                            CompactRaceTabs(state, onSelect)
                        }
                    },
                    detail = {
                        selectedDriver?.let {
                            DriverDetail(
                                it, state.history, state.race.bests, state.info, state.race.player,
                                onClose = if (layout.mode == PaneMode.SIDE_BY_SIDE) onClearSelection else null,
                            )
                        }
                    },
                    idleDetail = {
                        Column(Modifier.fillMaxSize().padding(top = 4.dp, end = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            PitHelperCard(state.pitAdvice, Modifier.fillMaxWidth())
                            EventFeed(state.events, Modifier.fillMaxWidth().weight(1f))
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun CompactRaceTabs(state: RaceUiState, onSelect: (Int) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = tab) {
            listOf("Tower", "Race control", "Strategy").forEachIndexed { i, title ->
                Tab(
                    selected = tab == i,
                    onClick = { tab = i },
                    text = { Text(title) },
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        when (tab) {
            0 -> TimingTower(state.race.drivers, state.race.bests, state.selected, onSelect)
            1 -> EventFeed(state.events, Modifier.fillMaxSize().padding(12.dp))
            else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
                PitHelperCard(state.pitAdvice, Modifier.fillMaxWidth())
            }
        }
    }
}

@PreviewScreenSizes
@Preview(name = "Tablet", device = Devices.TABLET)
@Composable
private fun RacePreview() {
    DashwroomTheme {
        val race = PreviewData.race()
        val state = RaceUiState(
            receiving = true, info = PreviewData.info(), race = race, history = PreviewData.history(),
            events = PreviewData.events(), pitAdvice = PitStrategy.compute(race, PreviewData.info()),
        )
        RaceContent(state, PreviewData.hot(), PreviewData.frame(), {}, {}, {})
    }
}
