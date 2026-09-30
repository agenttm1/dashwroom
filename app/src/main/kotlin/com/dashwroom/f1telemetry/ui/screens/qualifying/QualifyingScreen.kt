package com.dashwroom.f1telemetry.ui.screens.qualifying

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dashwroom.f1telemetry.core.model.DriverState
import com.dashwroom.f1telemetry.core.model.DriverStatus
import com.dashwroom.f1telemetry.core.model.SessionBests
import com.dashwroom.f1telemetry.core.state.HotTelemetry
import com.dashwroom.f1telemetry.data.settings.DeltaReference
import com.dashwroom.f1telemetry.ui.adaptive.ListDetail
import com.dashwroom.f1telemetry.ui.adaptive.PaneLayoutInfo
import com.dashwroom.f1telemetry.ui.adaptive.PaneMode
import com.dashwroom.f1telemetry.ui.adaptive.rememberPaneLayout
import com.dashwroom.f1telemetry.ui.components.Palette
import com.dashwroom.f1telemetry.ui.components.SessionHeader
import com.dashwroom.f1telemetry.ui.components.TeamStripe
import com.dashwroom.f1telemetry.ui.components.TyreBadge
import com.dashwroom.f1telemetry.ui.components.WaitingForTelemetry
import com.dashwroom.f1telemetry.ui.format.Fmt
import com.dashwroom.f1telemetry.ui.hot.HotDeltaBar
import com.dashwroom.f1telemetry.ui.hot.HotLapTime
import com.dashwroom.f1telemetry.ui.hot.rememberHotFrame
import com.dashwroom.f1telemetry.ui.preview.PreviewData
import com.dashwroom.f1telemetry.ui.screens.race.DriverDetail
import com.dashwroom.f1telemetry.ui.theme.DashColors
import com.dashwroom.f1telemetry.ui.theme.DashTheme
import com.dashwroom.f1telemetry.ui.theme.DashwroomTheme
import com.dashwroom.f1telemetry.ui.theme.TeamColors
import kotlinx.collections.immutable.toImmutableList

@Composable
fun QualifyingScreen(onOpenConnect: () -> Unit, viewModel: QualifyingViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val frame = rememberHotFrame(viewModel.hot)
    QualifyingContent(state, viewModel.hot, frame, viewModel::select, viewModel::clearSelection, onOpenConnect)
}

/**
 * Leaderboard by best lap with purple/green/yellow sectors, cars on a flying lap and their live
 * sector deltas, the knock-out line, and the player's live delta to their PB and to pole.
 */
@Composable
fun QualifyingContent(
    state: QualifyingUiState,
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
                title = "Waiting for qualifying",
                message = "The leaderboard fills in as cars set lap times. Works in practice and time trial too.",
                actionLabel = "Connection setup",
                onAction = onOpenConnect,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            val selectedDriver = state.race.driver(state.selected)
            val player = state.race.player
            ListDetail(
                hasSelection = selectedDriver != null,
                onDismissDetail = onClearSelection,
                layout = layout,
                modifier = Modifier.fillMaxSize(),
                list = {
                    Column(Modifier.fillMaxSize()) {
                        LivePanel(hot, frame, Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp))
                        Leaderboard(state, onSelect, Modifier.weight(1f))
                    }
                },
                detail = {
                    selectedDriver?.let {
                        DriverDetail(it, state.history, state.race.bests, state.info, player, onClose = if (layout.mode == PaneMode.SIDE_BY_SIDE) onClearSelection else null)
                    }
                },
                idleDetail = {
                    Column(Modifier.fillMaxSize().padding(top = 8.dp, end = 12.dp)) {
                        SessionHeader(state.info, state.race.leaderLap, Modifier.fillMaxWidth().padding(bottom = 4.dp))
                        if (player != null) DriverDetail(player, state.history, state.race.bests, state.info, null, onClose = null, modifier = Modifier.weight(1f))
                    }
                },
            )
        }
    }
}

@Composable
private fun LivePanel(hot: HotTelemetry, frame: State<Long>, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(16.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(0.8f)) {
            Text("LAP", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HotLapTime(hot, frame, fontSize = 26)
        }
        Column(Modifier.weight(1f)) {
            Text("Δ PERSONAL BEST", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HotDeltaBar(hot, frame, DeltaReference.PERSONAL_BEST)
        }
        Column(Modifier.weight(1f)) {
            Text("Δ SESSION BEST", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HotDeltaBar(hot, frame, DeltaReference.SESSION_BEST)
        }
    }
}

private data class QualiColumns(val gap: Boolean, val sectors: Boolean, val live: Boolean) {
    companion object {
        fun forWidth(w: Dp) = QualiColumns(gap = w >= 380.dp, sectors = w >= 560.dp, live = w >= 460.dp)
    }
}

@Composable
private fun Leaderboard(state: QualifyingUiState, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val ko = state.knockout
    BoxWithConstraints(modifier.fillMaxSize()) {
        val columns = QualiColumns.forWidth(maxWidth)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)) {
            item(key = "header", contentType = "header") { Header(columns, ko) }
            state.leaderboard.forEachIndexed { i, d ->
                val pos = i + 1
                if (ko != null && ko.firstEliminated > 0 && pos == ko.firstEliminated) {
                    item(key = "cutoff", contentType = "cutoff") { CutoffLine(ko, Modifier.animateItem()) }
                }
                item(key = d.vehicleIndex, contentType = "driver") {
                    LeaderRow(
                        d = d,
                        rank = pos,
                        poleMs = state.poleMs,
                        bests = state.race.bests,
                        columns = columns,
                        eliminated = ko != null && ko.firstEliminated in 1..pos,
                        selected = d.vehicleIndex == state.selected,
                        onClick = { onSelect(d.vehicleIndex) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

@Composable
private fun Header(columns: QualiColumns, ko: Knockout?) {
    val style = MaterialTheme.typography.labelSmall
    val c = MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(ko?.part ?: "POS", style = style, color = c, modifier = Modifier.width(34.dp))
        Text("DRIVER", style = style, color = c, modifier = Modifier.width(86.dp))
        Text("BEST", style = style, color = c, modifier = Modifier.weight(1.2f), textAlign = TextAlign.End)
        if (columns.gap) Text("GAP", style = style, color = c, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        if (columns.sectors) for (s in 1..3) Text("S$s", style = style, color = c, modifier = Modifier.weight(0.9f), textAlign = TextAlign.End)
        if (columns.live) Text("NOW", style = style, color = c, modifier = Modifier.weight(1.3f), textAlign = TextAlign.End)
    }
}

@Composable
private fun CutoffLine(ko: Knockout, modifier: Modifier = Modifier) {
    val danger = DashTheme.colors.danger
    Column(modifier.fillMaxWidth().padding(vertical = 4.dp).semantics { contentDescription = "Elimination line: ${ko.eliminatedCount} cars knocked out in ${ko.part}" }) {
        HorizontalDivider(thickness = 2.dp, color = danger)
        Text(
            "KNOCK-OUT ZONE · ${ko.eliminatedCount} OUT",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = danger,
            modifier = Modifier.padding(start = 8.dp, top = 2.dp),
        )
    }
}

@Composable
private fun LeaderRow(
    d: DriverState,
    rank: Int,
    poleMs: Long,
    bests: SessionBests,
    columns: QualiColumns,
    eliminated: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DashTheme.colors
    val scheme = MaterialTheme.colorScheme
    val bg = when {
        d.isPlayer -> scheme.primaryContainer.copy(alpha = 0.45f)
        eliminated -> colors.danger.copy(alpha = 0.08f)
        else -> Color.Transparent
    }
    val body = MaterialTheme.typography.bodyLarge
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(vertical = 1.dp)
            .background(bg, RoundedCornerShape(8.dp))
            .then(if (selected) Modifier.border(BorderStroke(2.dp, scheme.secondary), RoundedCornerShape(8.dp)) else Modifier)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                this.selected = selected
                contentDescription = "P$rank ${d.name}, best ${Fmt.lapTime(d.bestLapTimeMs)}" +
                    (if (d.isOnFlyingLap) ", on a flying lap" else "") + (if (eliminated) ", in the knock-out zone" else "")
            }
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(rank.toString(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(26.dp), textAlign = TextAlign.End)
        Spacer(Modifier.width(8.dp))
        TeamStripe(TeamColors.of(d.teamId, d.liveryColour))
        Text(d.code, style = MaterialTheme.typography.titleMedium, fontWeight = if (d.isPlayer) FontWeight.Bold else FontWeight.SemiBold, modifier = Modifier.padding(start = 6.dp).width(48.dp))
        TyreBadge(d.tyreVisual, size = 20.dp)
        Text(
            Fmt.lapTime(d.bestLapTimeMs),
            style = body,
            color = if (d.bestLapTimeMs > 0 && d.vehicleIndex == bests.lapVehicle) colors.sessionBest else scheme.onSurface,
            modifier = Modifier.weight(1.2f),
            textAlign = TextAlign.End,
            maxLines = 1,
        )
        if (columns.gap) {
            val gap = if (rank == 1 || d.bestLapTimeMs <= 0 || poleMs <= 0) "" else Fmt.gap((d.bestLapTimeMs - poleMs).toInt())
            Text(gap, style = body, color = scheme.onSurfaceVariant, modifier = Modifier.weight(1f), textAlign = TextAlign.End, maxLines = 1)
        }
        if (columns.sectors) {
            for (s in 0..2) {
                val ms = d.bestSectorsMs?.get(s) ?: 0
                Text(
                    Fmt.sector(ms),
                    style = MaterialTheme.typography.bodyMedium,
                    color = sectorColor(ms, bests.sectorMs(s), colors) ?: scheme.onSurfaceVariant,
                    modifier = Modifier.weight(0.9f),
                    textAlign = TextAlign.End,
                    maxLines = 1,
                )
            }
        }
        if (columns.live) LiveStatus(d, bests, colors, Modifier.weight(1.3f))
    }
}

/** Best-sector colour in the leaderboard: purple = fastest of anyone, green = everyone else's PB. */
private fun sectorColor(ms: Int, sessionBest: Int, colors: DashColors): Color? = when {
    ms <= 0 -> null
    sessionBest > 0 && ms <= sessionBest -> colors.sessionBest
    else -> colors.personalBest
}

/** On a flying lap: the latest completed sector's delta to the driver's own best sector. */
@Composable
private fun LiveStatus(d: DriverState, bests: SessionBests, colors: DashColors, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val text: String
    val color: Color
    when (d.driverStatus) {
        DriverStatus.FLYING_LAP -> {
            val done = d.sector // sectors completed this lap
            val current = if (done >= 2) d.currentSector2Ms else if (done >= 1) d.currentSector1Ms else 0
            val best = d.bestSectorsMs?.get(done - 1) ?: 0
            if (done >= 1 && current > 0 && best > 0) {
                val delta = current - best
                text = "S$done ${Fmt.delta(delta)}"
                color = when {
                    current <= bests.sectorMs(done - 1) -> colors.sessionBest
                    delta <= 0 -> colors.personalBest
                    else -> colors.slower
                }
            } else {
                text = "● ON LAP"
                color = colors.personalBest
            }
        }
        DriverStatus.OUT_LAP -> { text = "Out lap"; color = scheme.onSurfaceVariant }
        DriverStatus.IN_LAP -> { text = "In lap"; color = scheme.onSurfaceVariant }
        DriverStatus.IN_GARAGE -> { text = "Garage"; color = scheme.onSurfaceVariant.copy(alpha = 0.6f) }
        DriverStatus.ON_TRACK -> { text = "On track"; color = scheme.onSurfaceVariant }
    }
    Text(text, style = MaterialTheme.typography.labelLarge, color = color, modifier = modifier, textAlign = TextAlign.End, maxLines = 1)
}

@PreviewScreenSizes
@Preview(name = "Tablet", device = Devices.TABLET)
@Composable
private fun QualifyingPreview() {
    DashwroomTheme {
        val race = PreviewData.race(race = false)
        val info = PreviewData.info(race = false).copy(sessionType = 5, sessionTypeName = "Qualifying 1")
        val ranked = QualifyingUiState.rank(race.drivers)
        val state = QualifyingUiState(true, info, race, ranked.toImmutableList(), PreviewData.history(), Knockout.of(info, ranked.size))
        QualifyingContent(state, PreviewData.hot(), PreviewData.frame(), {}, {}, {})
    }
}
