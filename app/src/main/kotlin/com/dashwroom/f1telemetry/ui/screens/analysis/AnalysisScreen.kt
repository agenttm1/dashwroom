package com.dashwroom.f1telemetry.ui.screens.analysis

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CompareArrows
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dashwroom.f1telemetry.ui.adaptive.PaneLayoutInfo
import com.dashwroom.f1telemetry.ui.adaptive.PaneMode
import com.dashwroom.f1telemetry.ui.adaptive.TwoPane
import com.dashwroom.f1telemetry.ui.adaptive.rememberPaneLayout
import com.dashwroom.f1telemetry.ui.components.DashCard
import com.dashwroom.f1telemetry.ui.components.TyreBadge
import com.dashwroom.f1telemetry.ui.components.WaitingForTelemetry
import com.dashwroom.f1telemetry.ui.format.Fmt
import com.dashwroom.f1telemetry.ui.preview.PreviewData
import com.dashwroom.f1telemetry.ui.theme.DashTheme
import com.dashwroom.f1telemetry.ui.theme.DashwroomTheme
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

@Composable
fun AnalysisScreen(onOpenConnect: () -> Unit, viewModel: AnalysisViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AnalysisContent(
        state,
        onSelectSession = viewModel::selectSession,
        onDeleteSession = viewModel::deleteSession,
        onSelectLap = viewModel::selectLap,
        onCompareLap = viewModel::compareLap,
        onOpenConnect = onOpenConnect,
    )
}

/**
 * Lap table (sector times, pick lap A, toggle comparison lap B) and the trace chart. Side by
 * side on large windows (split at the hinge on foldables); stacked on phones.
 */
@Composable
fun AnalysisContent(
    state: AnalysisUiState,
    onSelectSession: (Long) -> Unit,
    onDeleteSession: (Long) -> Unit,
    onSelectLap: (Int) -> Unit,
    onCompareLap: (Int) -> Unit,
    onOpenConnect: () -> Unit,
    modifier: Modifier = Modifier,
    layout: PaneLayoutInfo = rememberPaneLayout(),
) {
    // Fade from the waiting skeleton to the real content when data starts flowing.
    Crossfade(state.sessions.isNotEmpty(), modifier, label = "data") { ready ->
        if (!ready) {
            WaitingForTelemetry(
                title = "No laps yet",
                message = "Complete a lap in F1 25 and its speed, throttle, brake, gear and steering traces appear here. Every lap is saved for later comparison.",
                actionLabel = "Connection setup",
                onAction = onOpenConnect,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            val chart: @Composable (Modifier) -> Unit = { m ->
                DashCard(m, title = "Telemetry") { TraceChart(state.traceA, state.traceB, Modifier.fillMaxSize()) }
            }
            val table: @Composable (Modifier) -> Unit = { m ->
                Column(m) {
                    SessionPicker(state, onSelectSession, onDeleteSession)
                    Spacer(Modifier.size(8.dp))
                    LapTable(state, onSelectLap, onCompareLap, Modifier.weight(1f))
                }
            }
            if (layout.mode == PaneMode.SIDE_BY_SIDE) {
                TwoPane(
                    first = { table(Modifier.fillMaxSize().padding(start = 12.dp, top = 12.dp, bottom = 12.dp)) },
                    second = { chart(Modifier.fillMaxSize().padding(top = 12.dp, end = 12.dp, bottom = 12.dp)) },
                    modifier = Modifier.fillMaxSize(),
                    firstWeight = 0.42f,
                    hinge = layout.hinge,
                )
            } else {
                Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    chart(Modifier.fillMaxWidth().weight(1.25f))
                    table(Modifier.fillMaxWidth().weight(1f))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionPicker(state: AnalysisUiState, onSelect: (Long) -> Unit, onDelete: (Long) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val current = state.sessions.firstOrNull { it.uid == state.sessionUid } ?: state.sessions.first()
    Row(verticalAlignment = Alignment.CenterVertically) {
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = Modifier.weight(1f)) {
            OutlinedTextField(
                value = current.title,
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                label = { Text(if (current.live) "Session (live)" else "Session") },
                supportingText = { Text(current.subtitle, maxLines = 1) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                state.sessions.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(option.title)
                                Text(option.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        onClick = {
                            expanded = false
                            onSelect(option.uid)
                        },
                    )
                }
            }
        }
        if (!current.live) {
            IconButton(onClick = { onDelete(current.uid) }) { Icon(Icons.Outlined.Delete, contentDescription = "Delete this session's laps") }
        }
    }
}

@Composable
private fun LapTable(state: AnalysisUiState, onSelect: (Int) -> Unit, onCompare: (Int) -> Unit, modifier: Modifier) {
    val colors = DashTheme.colors
    val best = state.bestLapMs
    val bestSectors = IntArray(3) { state.bestSector(it) }
    DashCard(modifier, title = "Laps · tap to view, ⇄ to compare") {
        if (state.laps.isEmpty()) {
            Text("No completed laps in this session yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@DashCard
        }
        Row(Modifier.padding(bottom = 4.dp)) {
            val st = MaterialTheme.typography.labelSmall
            val c = MaterialTheme.colorScheme.onSurfaceVariant
            Text("LAP", style = st, color = c, modifier = Modifier.width(64.dp))
            Text("TIME", style = st, color = c, modifier = Modifier.weight(1.3f), textAlign = TextAlign.End)
            for (s in 1..3) Text("S$s", style = st, color = c, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
            Spacer(Modifier.width(48.dp))
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 4.dp)) {
            items(state.laps, key = { it.lap }) { lap ->
                val isA = lap.lap == state.lapA
                val isB = lap.lap == state.lapB
                val bg = when {
                    isA -> LapAColor.copy(alpha = 0.18f)
                    isB -> LapBColor.copy(alpha = 0.18f)
                    else -> Color.Transparent
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .background(bg, RoundedCornerShape(8.dp))
                        .clickable(enabled = lap.hasTrace) { onSelect(lap.lap) }
                        .semantics(mergeDescendants = true) {
                            role = Role.Button
                            selected = isA
                            contentDescription = "Lap ${lap.lap}, ${Fmt.lapTime(lap.lapTimeMs)}" +
                                (if (!lap.valid) ", invalid" else "") + (if (!lap.hasTrace) ", no telemetry recorded" else "")
                        }
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(Modifier.width(58.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${lap.lap}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(30.dp))
                        if (lap.tyreVisual > 0) TyreBadge(lap.tyreVisual, size = 18.dp)
                    }
                    val timeColor = when {
                        !lap.valid -> colors.danger
                        lap.lapTimeMs == best -> colors.sessionBest
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                    Text(
                        Fmt.lapTime(lap.lapTimeMs),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isA || isB) FontWeight.Bold else FontWeight.Normal,
                        color = timeColor.copy(alpha = if (lap.hasTrace) 1f else 0.6f),
                        modifier = Modifier.weight(1.3f),
                        textAlign = TextAlign.End,
                    )
                    for (s in 0..2) {
                        val ms = lap.sectors.get(s)
                        Text(
                            Fmt.sector(ms),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (ms > 0 && ms == bestSectors[s]) colors.sessionBest else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.End,
                        )
                    }
                    IconToggleButton(checked = isB, onCheckedChange = { onCompare(lap.lap) }, enabled = lap.hasTrace && !isA) {
                        Icon(
                            Icons.Outlined.CompareArrows,
                            contentDescription = if (isB) "Stop comparing lap ${lap.lap}" else "Compare with lap ${lap.lap}",
                            tint = if (isB) LapBColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@PreviewScreenSizes
@Preview(name = "Tablet", device = Devices.TABLET)
@Composable
private fun AnalysisPreview() {
    DashwroomTheme { AnalysisContent(PreviewData.analysis(), {}, {}, {}, {}, {}) }
}
