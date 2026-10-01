package com.dashwroom.f1telemetry.ui.screens.drive

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dashwroom.f1telemetry.core.model.DriverState
import com.dashwroom.f1telemetry.core.model.SessionBests
import com.dashwroom.f1telemetry.core.state.HotTelemetry
import com.dashwroom.f1telemetry.data.settings.DeltaReference
import com.dashwroom.f1telemetry.data.settings.DrivePreset
import com.dashwroom.f1telemetry.ui.components.Palette
import com.dashwroom.f1telemetry.ui.components.WaitingForTelemetry
import com.dashwroom.f1telemetry.ui.format.Fmt
import com.dashwroom.f1telemetry.ui.format.LocalDisplayPrefs
import com.dashwroom.f1telemetry.ui.hot.HotDeltaBar
import com.dashwroom.f1telemetry.ui.hot.HotErsBar
import com.dashwroom.f1telemetry.ui.hot.HotLapTime
import com.dashwroom.f1telemetry.ui.hot.rememberHotFrame
import com.dashwroom.f1telemetry.ui.navigation.LocalImmersive
import com.dashwroom.f1telemetry.ui.preview.PreviewData
import com.dashwroom.f1telemetry.ui.theme.DashColors
import com.dashwroom.f1telemetry.ui.theme.DashTheme
import com.dashwroom.f1telemetry.ui.theme.DashwroomTheme

@Composable
fun DriveScreen(onOpenConnect: () -> Unit, viewModel: DriveViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val frame = rememberHotFrame(viewModel.hot)
    val immersive = LocalImmersive.current
    BackHandler(enabled = immersive.enabled) { immersive.enabled = false }
    DriveContent(
        state = state,
        hot = viewModel.hot,
        frame = frame,
        onPreset = viewModel::setPreset,
        immersive = immersive.enabled,
        onToggleImmersive = { immersive.enabled = !immersive.enabled },
        onOpenConnect = onOpenConnect,
    )
}

/**
 * What to look at while driving: rev lights, a huge gear, speed, pedal inputs and the timing that
 * matters for the session — picked by a preset (Auto, Race, Quali, Time trial, Minimal). All the
 * live values redraw at the display's refresh rate without recomposition.
 */
@Composable
fun DriveContent(
    state: DriveUiState,
    hot: HotTelemetry,
    frame: State<Long>,
    onPreset: (DrivePreset) -> Unit,
    immersive: Boolean,
    onToggleImmersive: () -> Unit,
    onOpenConnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Crossfade(state.receiving, modifier, label = "data") { ready ->
        if (!ready) {
            WaitingForTelemetry(
                title = "Waiting for the car",
                message = "Gear, speed and pedals appear here as soon as F1 25 sends car telemetry.",
                actionLabel = "Connection setup",
                onAction = onOpenConnect,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PresetBar(state, onPreset, immersive, onToggleImmersive)
                HotRevBar(hot, frame, Modifier.fillMaxWidth().height(22.dp))
                BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                    val wide = maxWidth > maxHeight * 1.15f
                    if (wide) WideCockpit(state, hot, frame) else TallCockpit(state, hot, frame)
                }
                val tiles = tilesFor(state, DashTheme.colors)
                if (tiles.isNotEmpty()) TileRow(tiles)
            }
        }
    }
}

@Composable
private fun PresetBar(state: DriveUiState, onPreset: (DrivePreset) -> Unit, immersive: Boolean, onToggleImmersive: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (p in DrivePreset.entries) {
                val label = if (p == DrivePreset.AUTO && state.presetSetting == DrivePreset.AUTO) "Auto · ${state.preset.label}" else p.label
                FilterChip(selected = state.presetSetting == p, onClick = { onPreset(p) }, label = { Text(label) })
            }
        }
        IconButton(onClick = onToggleImmersive) {
            Icon(
                if (immersive) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen,
                contentDescription = if (immersive) "Exit full screen" else "Full screen",
            )
        }
    }
}

/** Landscape: pedals | gear + speed | session panel. */
@Composable
private fun WideCockpit(state: DriveUiState, hot: HotTelemetry, frame: State<Long>) {
    val preset = state.preset
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Panel(Modifier.weight(0.75f).fillMaxHeight()) {
            HotPedals(hot, frame, Modifier.fillMaxWidth().weight(1f))
            Spacer(Modifier.height(8.dp))
            HotSteering(hot, frame, Modifier.fillMaxWidth().height(14.dp))
            if (preset == DrivePreset.TIME_TRIAL || preset == DrivePreset.MINIMAL) {
                Spacer(Modifier.height(8.dp))
                HotPedalTrace(hot, frame, Modifier.fillMaxWidth().weight(0.5f))
            }
        }
        Panel(Modifier.weight(1.1f).fillMaxHeight()) {
            HotGear(hot, frame, Modifier.fillMaxWidth().weight(1.5f))
            HotSpeedBig(hot, frame, Modifier.fillMaxWidth().weight(0.7f))
        }
        Panel(Modifier.weight(1.35f).fillMaxHeight()) { SessionPanel(state, hot, frame) }
    }
}

/** Portrait: gear + (speed, pedals) on top, session panel below. */
@Composable
private fun TallCockpit(state: DriveUiState, hot: HotTelemetry, frame: State<Long>) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().weight(1.1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Panel(Modifier.weight(1.1f).fillMaxHeight()) {
                HotGear(hot, frame, Modifier.fillMaxWidth().weight(1.4f))
                HotSpeedBig(hot, frame, Modifier.fillMaxWidth().weight(0.8f))
            }
            Panel(Modifier.weight(0.9f).fillMaxHeight()) {
                HotPedals(hot, frame, Modifier.fillMaxWidth().weight(1f))
                Spacer(Modifier.height(8.dp))
                HotSteering(hot, frame, Modifier.fillMaxWidth().height(12.dp))
            }
        }
        Panel(Modifier.fillMaxWidth().weight(1f)) { SessionPanel(state, hot, frame) }
    }
}

/** The preset-specific timing column. */
@Composable
private fun ColumnScope.SessionPanel(state: DriveUiState, hot: HotTelemetry, frame: State<Long>) {
    val reference = LocalDisplayPrefs.current.deltaReference
    when (state.preset) {
        DrivePreset.RACE, DrivePreset.AUTO -> {
            Label("Lap time")
            HotLapTime(hot, frame, fontSize = 34)
            Label("Δ ${reference.label}")
            HotDeltaBar(hot, frame, reference, height = 52.dp, fontSize = 26)
            Spacer(Modifier.height(10.dp))
            Label("ERS")
            HotErsBar(hot, frame)
            Label("Throttle / brake")
            HotPedalTrace(hot, frame, Modifier.fillMaxWidth().weight(1f).heightIn(min = 40.dp))
            Spacer(Modifier.height(8.dp))
            HotFlags(hot, frame, Modifier.fillMaxWidth().height(34.dp))
        }
        DrivePreset.QUALI -> {
            Label("Lap time")
            HotLapTime(hot, frame, fontSize = 34)
            Label("Δ personal best")
            HotDeltaBar(hot, frame, DeltaReference.PERSONAL_BEST, height = 48.dp, fontSize = 24)
            Label("Δ session best")
            HotDeltaBar(hot, frame, DeltaReference.SESSION_BEST, height = 40.dp, fontSize = 20)
            Spacer(Modifier.height(10.dp))
            SectorBoxes(state.player, state.bests, Modifier.fillMaxWidth())
            Label("Throttle / brake")
            HotPedalTrace(hot, frame, Modifier.fillMaxWidth().weight(1f).heightIn(min = 40.dp))
            Spacer(Modifier.height(8.dp))
            HotFlags(hot, frame, Modifier.fillMaxWidth().height(34.dp))
        }
        DrivePreset.TIME_TRIAL -> {
            Label("Lap time")
            HotLapTime(hot, frame, fontSize = 38)
            Label("Δ personal best")
            HotDeltaBar(hot, frame, DeltaReference.PERSONAL_BEST, rangeMs = 500, height = 64.dp, fontSize = 30)
            Spacer(Modifier.height(10.dp))
            SectorBoxes(state.player, state.bests, Modifier.fillMaxWidth())
            Spacer(Modifier.weight(1f))
            HotFlags(hot, frame, Modifier.fillMaxWidth().height(34.dp))
        }
        DrivePreset.MINIMAL -> {
            Label("Lap time")
            HotLapTime(hot, frame, fontSize = 34)
            Label("Δ ${reference.label}")
            HotDeltaBar(hot, frame, reference, height = 52.dp, fontSize = 26)
            Spacer(Modifier.weight(1f))
            HotFlags(hot, frame, Modifier.fillMaxWidth().height(34.dp))
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp, bottom = 2.dp))
}

@Composable
private fun Panel(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(16.dp)).padding(12.dp),
        content = content,
    )
}

/**
 * S1–S3 of the current lap: a completed sector shows its time coloured against the session best
 * (purple), your best (green) or slower (yellow); the sector you're in is outlined.
 */
@Composable
fun SectorBoxes(player: DriverState?, bests: SessionBests, modifier: Modifier = Modifier) {
    val colors = DashTheme.colors
    val scheme = MaterialTheme.colorScheme
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (i in 0..2) {
            val done = player != null && i < player.sector
            val ms = when {
                !done -> 0
                i == 0 -> player!!.currentSector1Ms
                else -> player!!.currentSector2Ms
            }
            val pb = player?.bestSectorsMs?.get(i) ?: 0
            val color = Palette.timing(colors, ms, pb, bests.sectorMs(i))
            val current = player != null && i == player.sector
            val delta = if (ms > 0 && pb > 0) Fmt.delta(ms - pb) else null
            Column(
                Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp)
                    .background(color?.copy(alpha = 0.22f) ?: scheme.surfaceContainerHigh, RoundedCornerShape(10.dp))
                    .then(if (current) Modifier.border(BorderStroke(2.dp, scheme.secondary), RoundedCornerShape(10.dp)) else Modifier)
                    .padding(8.dp)
                    .semantics(mergeDescendants = true) {
                        contentDescription = "Sector ${i + 1}: " + when {
                            ms > 0 -> "${Fmt.sector(ms)}" + (delta?.let { ", $it to your best" } ?: "")
                            current -> "in progress"
                            else -> "not reached"
                        }
                    },
            ) {
                Text("S${i + 1}", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                Text(if (ms > 0) Fmt.sector(ms) else if (current) "…" else "—", style = MaterialTheme.typography.titleMedium, color = color ?: scheme.onSurface)
                if (delta != null) Text(delta, style = MaterialTheme.typography.bodySmall, color = color ?: scheme.onSurfaceVariant)
            }
        }
    }
}

@Immutable
data class DriveTile(val label: String, val value: String, val color: Color? = null)

/** The glanceable numbers under the cockpit, per preset. Cold data (≤ 10 Hz). */
fun tilesFor(state: DriveUiState, colors: DashColors): List<DriveTile> {
    val p = state.player
    val car = state.car
    val info = state.info
    val tyreWear = car.wheels.maxOfOrNull { it.wearPercent } ?: 0f
    val tyres = DriveTile("Tyres", "${car.tyreAgeLaps} L · ${tyreWear.toInt()}%", Palette.damage(tyreWear))
    return when (state.preset) {
        DrivePreset.RACE, DrivePreset.AUTO -> listOf(
            DriveTile("Position", if (p != null && p.position > 0) "P${p.position}/${state.numCars}" else Fmt.NONE),
            DriveTile("Lap", if (info != null && info.totalLaps > 0) "${p?.currentLap ?: state.leaderLap}/${info.totalLaps}" else (p?.currentLap?.toString() ?: Fmt.NONE)),
            DriveTile("Ahead", state.ahead?.let { "${it.code} ${Fmt.gap(p?.intervalMs ?: 0)}" } ?: Fmt.NONE),
            DriveTile("Behind", state.behind?.let { "${it.code} ${Fmt.gap(it.intervalMs)}" } ?: Fmt.NONE),
            DriveTile(
                "Fuel",
                "${Fmt.signedOneDecimal(car.fuelRemainingLaps)} L",
                when {
                    car.fuelRemainingLaps < -0.2f -> colors.danger
                    car.fuelRemainingLaps < 0.3f -> colors.warning
                    else -> colors.personalBest
                },
            ),
            tyres,
        )
        DrivePreset.QUALI -> listOf(
            DriveTile("Last lap", Fmt.lapTime(p?.lastLapTimeMs ?: 0)),
            DriveTile("Best lap", Fmt.lapTime(p?.bestLapTimeMs ?: 0), colors.personalBest),
            DriveTile("Session best", Fmt.lapTime(state.bests.lapMs), colors.sessionBest),
            DriveTile("Position", if (p != null && p.position > 0) "P${p.position}" else Fmt.NONE),
            DriveTile("Time left", info?.let { Fmt.clock(it.sessionTimeLeftS) } ?: Fmt.NONE),
            tyres,
        )
        DrivePreset.TIME_TRIAL -> {
            val bs = p?.bestSectorsMs
            val theoretical = if (bs != null && bs.s1Ms > 0 && bs.s2Ms > 0 && bs.s3Ms > 0) (bs.s1Ms + bs.s2Ms + bs.s3Ms).toLong() else 0L
            listOf(
                DriveTile("Last lap", Fmt.lapTime(p?.lastLapTimeMs ?: 0)),
                DriveTile("Personal best", Fmt.lapTime(p?.bestLapTimeMs ?: 0), colors.personalBest),
                DriveTile("Theoretical", Fmt.lapTime(theoretical), colors.sessionBest),
                DriveTile("Lap", p?.currentLap?.toString() ?: Fmt.NONE),
                tyres,
            )
        }
        DrivePreset.MINIMAL -> emptyList()
    }
}

/** One row on wide screens; rows of three on narrow ones so values stay readable. */
@Composable
private fun TileRow(tiles: List<DriveTile>) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val perRow = if (maxWidth >= 600.dp) tiles.size else 3
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (row in tiles.chunked(perRow)) TileLine(row, perRow)
        }
    }
}

@Composable
private fun TileLine(tiles: List<DriveTile>, slots: Int) {
    Row(Modifier.fillMaxWidth().height(68.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (t in tiles) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .semantics(mergeDescendants = true) { contentDescription = "${t.label}: ${t.value}" },
                verticalArrangement = Arrangement.Center,
            ) {
                Text(t.label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                BasicText(
                    t.value,
                    style = MaterialTheme.typography.headlineSmall.copy(color = t.color ?: MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = 26.sp),
                )
            }
        }
        repeat(slots - tiles.size) { Spacer(Modifier.weight(1f)) }
    }
}

@PreviewScreenSizes
@Preview(name = "Tablet", device = Devices.TABLET)
@Composable
private fun DrivePreview() {
    DashwroomTheme {
        DriveContent(PreviewData.drive(DrivePreset.RACE), PreviewData.hot(), PreviewData.frame(), {}, false, {}, {})
    }
}
