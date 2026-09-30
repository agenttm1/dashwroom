package com.dashwroom.f1telemetry.ui.screens.race

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dashwroom.f1telemetry.core.model.DriverHistory
import com.dashwroom.f1telemetry.core.model.DriverState
import com.dashwroom.f1telemetry.core.model.HistoryState
import com.dashwroom.f1telemetry.core.model.RaceEvent
import com.dashwroom.f1telemetry.core.model.RaceEventType
import com.dashwroom.f1telemetry.core.model.SessionBests
import com.dashwroom.f1telemetry.core.model.SessionInfo
import com.dashwroom.f1telemetry.ui.components.DashCard
import com.dashwroom.f1telemetry.ui.components.LapPositionChart
import com.dashwroom.f1telemetry.ui.components.MetricTile
import com.dashwroom.f1telemetry.ui.components.Palette
import com.dashwroom.f1telemetry.ui.components.StintBar
import com.dashwroom.f1telemetry.ui.components.TeamStripe
import com.dashwroom.f1telemetry.ui.components.TyreBadge
import com.dashwroom.f1telemetry.ui.format.Fmt
import com.dashwroom.f1telemetry.ui.theme.DashColors
import com.dashwroom.f1telemetry.ui.theme.DashTheme
import com.dashwroom.f1telemetry.ui.theme.TeamColors
import kotlinx.collections.immutable.ImmutableList

/** Everything about one driver: sectors, tyre history, pit stops, lap times, positions by lap. */
@Composable
fun DriverDetail(
    driver: DriverState,
    history: HistoryState,
    bests: SessionBests,
    info: SessionInfo?,
    player: DriverState?,
    onClose: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = DashTheme.colors
    val h: DriverHistory? = history.drivers[driver.vehicleIndex]
    val team = TeamColors.of(driver.teamId, driver.liveryColour)
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(key = "head") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TeamStripe(team, height = 44.dp)
                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                    Text(
                        "P${driver.position}  ${driver.name}",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.semantics { heading() },
                    )
                    val gained = driver.gridPosition - driver.position
                    val grid = when {
                        driver.gridPosition <= 0 -> ""
                        gained > 0 -> " · +$gained from grid P${driver.gridPosition}"
                        gained < 0 -> " · $gained from grid P${driver.gridPosition}"
                        else -> " · started P${driver.gridPosition}"
                    }
                    Text("#${driver.raceNumber} ${driver.teamName}$grid", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (onClose != null) {
                    IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, contentDescription = "Close driver details") }
                }
            }
        }
        item(key = "sectors") { SectorsCard(driver, bests, colors) }
        item(key = "tyres") {
            DashCard(title = "Tyres & pit stops") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TyreBadge(driver.tyreVisual, size = 28.dp)
                    Text("  ${driver.tyreAgeLaps} laps", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    MetricTile("Stops", driver.numPitStops.toString())
                }
                if (h != null && h.stints.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    StintBar(h.stints, driver.currentLap, info?.totalLaps ?: 0)
                    val pitLaps = h.stints.mapNotNull { it.endLap }
                    if (pitLaps.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text("Pitted on lap ${pitLaps.joinToString(", ")}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (driver.penaltiesSeconds > 0 || driver.totalWarnings > 0 || driver.unservedDriveThroughs + driver.unservedStopGoes > 0) {
                    Spacer(Modifier.height(8.dp))
                    val parts = buildList {
                        if (driver.penaltiesSeconds > 0) add("+${driver.penaltiesSeconds} s penalty")
                        if (driver.totalWarnings > 0) add("${driver.totalWarnings} warnings")
                        if (driver.unservedDriveThroughs > 0) add("${driver.unservedDriveThroughs} drive-through unserved")
                        if (driver.unservedStopGoes > 0) add("${driver.unservedStopGoes} stop-go unserved")
                    }
                    Text(parts.joinToString(" · "), color = colors.danger, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (history.lapPositions.isNotEmpty()) {
            item(key = "positions") {
                DashCard(title = "Position by lap") {
                    val hl = buildList {
                        if (player != null && player.vehicleIndex != driver.vehicleIndex) add(player.vehicleIndex to MaterialTheme.colorScheme.onSurface)
                        add(driver.vehicleIndex to team)
                    }
                    LapPositionChart(history.lapPositions, hl, numCars = history.lapPositions.size, modifier = Modifier.fillMaxWidth().height(180.dp))
                }
            }
        }
        if (h != null && h.laps.isNotEmpty()) {
            item(key = "laps") { LapTimesCard(h, bests, colors) }
        }
    }
}

@Composable
private fun SectorsCard(d: DriverState, bests: SessionBests, colors: DashColors) {
    DashCard(title = "Sectors") {
        Row {
            Text("", Modifier.weight(0.9f))
            for (s in 1..3) Text("S$s", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
            Text("LAP", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1.3f), textAlign = TextAlign.End)
        }
        val current = intArrayOf(d.currentSector1Ms, d.currentSector2Ms, 0)
        SectorRow("Now", IntArray(3) { if (it < d.sector) current[it] else 0 }, d.currentLapTimeMs, d, bests, colors, live = true)
        SectorRow("Last", d.lastSectorsMs?.let { intArrayOf(it.s1Ms, it.s2Ms, it.s3Ms) } ?: IntArray(3), d.lastLapTimeMs, d, bests, colors)
        SectorRow("Best", d.bestSectorsMs?.let { intArrayOf(it.s1Ms, it.s2Ms, it.s3Ms) } ?: IntArray(3), d.bestLapTimeMs, d, bests, colors)
        d.liveDeltaToBestMs?.let {
            Spacer(Modifier.height(6.dp))
            Text("Live Δ to own best ${Fmt.delta(it)}", color = Palette.delta(colors, it), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SectorRow(label: String, sectors: IntArray, lapMs: Long, d: DriverState, bests: SessionBests, colors: DashColors, live: Boolean = false) {
    Row(Modifier.padding(vertical = 3.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.9f))
        for (i in 0..2) {
            val ms = sectors[i]
            val pb = d.bestSectorsMs?.get(i) ?: 0
            Text(
                Fmt.sector(ms),
                style = MaterialTheme.typography.bodyLarge,
                color = Palette.timing(colors, ms, pb, bests.sectorMs(i)) ?: MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End,
            )
        }
        Text(
            if (live && lapMs > 0) Fmt.lapTime(lapMs) else Fmt.lapTime(lapMs),
            style = MaterialTheme.typography.bodyLarge,
            color = if (live && d.currentLapInvalid) colors.danger else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1.3f),
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun LapTimesCard(h: DriverHistory, bests: SessionBests, colors: DashColors) {
    DashCard(title = "Lap times") {
        for (lap in h.laps.asReversed().take(15)) {
            Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("L${lap.lap}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(44.dp))
                if (lap.tyreVisual > 0) TyreBadge(lap.tyreVisual, size = 18.dp) else Spacer(Modifier.size(18.dp))
                val color = when {
                    !lap.valid -> colors.danger
                    bests.lapMs > 0 && lap.lapTimeMs <= bests.lapMs -> colors.sessionBest
                    lap.lap == h.bestLapNumber -> colors.personalBest
                    else -> MaterialTheme.colorScheme.onSurface
                }
                Text(Fmt.lapTime(lap.lapTimeMs), style = MaterialTheme.typography.bodyLarge, color = color, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                for (i in 0..2) {
                    val ms = lap.sectorsMs.get(i)
                    Text(Fmt.sector(ms), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.8f), textAlign = TextAlign.End)
                }
                if (!lap.valid) Text(" ✕", color = colors.danger, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** Race-control style feed, newest first; player-related lines are highlighted. */
@Composable
fun EventFeed(events: ImmutableList<RaceEvent>, modifier: Modifier = Modifier) {
    val colors = DashTheme.colors
    DashCard(modifier, title = "Race control") {
        if (events.isEmpty()) {
            Text("No events yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@DashCard
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(events, key = { it.id }, contentType = { "event" }) { e ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .animateItem()
                        .background(if (e.involvesPlayer) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else Color.Transparent, RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(8.dp).background(eventColor(e.type, colors), CircleShape))
                    Text(if (e.lap > 0) "L${e.lap}" else "", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp).width(36.dp))
                    Text(e.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

private fun eventColor(type: RaceEventType, c: DashColors): Color = when (type) {
    RaceEventType.FASTEST_LAP -> c.sessionBest
    RaceEventType.PENALTY, RaceEventType.RED_FLAG, RaceEventType.RETIREMENT, RaceEventType.COLLISION -> c.danger
    RaceEventType.SAFETY_CAR, RaceEventType.TEAM_MATE_IN_PITS -> c.warning
    RaceEventType.DRS_ENABLED, RaceEventType.DRS_DISABLED, RaceEventType.OVERTAKE, RaceEventType.OVERTAKE_MODE -> c.drs
    else -> c.searching
}

/** Pit window, projected rejoin position and undercut situation for the player. */
@Composable
fun PitHelperCard(advice: PitAdvice?, modifier: Modifier = Modifier) {
    val colors = DashTheme.colors
    DashCard(modifier, title = "Pit strategy") {
        if (advice == null) {
            Text("Available once you're racing", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@DashCard
        }
        val (windowText, windowColor) = when (advice.windowState) {
            PitAdvice.WindowState.NOT_SET -> "No window" to MaterialTheme.colorScheme.onSurfaceVariant
            PitAdvice.WindowState.BEFORE -> "Opens lap ${advice.idealLap}" to MaterialTheme.colorScheme.onSurface
            PitAdvice.WindowState.OPEN -> "Open" to colors.personalBest
            PitAdvice.WindowState.CLOSING -> "Closing — lap ${advice.latestLap}" to colors.warning
            PitAdvice.WindowState.PASSED -> "Passed (lap ${advice.latestLap})" to colors.danger
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            MetricTile("Window", windowText, Modifier.weight(1.4f), windowColor)
            MetricTile(
                if (advice.pitLossEstimated) "Pit loss (est.)" else "Pit loss",
                "${Fmt.oneDecimal(advice.pitLossMs / 1000f)} s",
                Modifier.weight(1f),
            )
            MetricTile("Rejoin", "P${advice.rejoinPosition}", Modifier.weight(0.8f))
        }
        if (advice.idealLap > 0) {
            Text(
                "Ideal lap ${advice.idealLap} · latest ${advice.latestLap} · now lap ${advice.currentLap}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (advice.neutralisedFactor < 1f) {
            Text("Cheaper stop now — the field is neutralised", color = colors.warning, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
        }
        Spacer(Modifier.height(10.dp))
        val rejoin = buildString {
            append("Rejoin")
            advice.rejoinBehind?.let { append(" behind ${it.code} (+${Fmt.oneDecimal(advice.rejoinBehindGapMs / 1000f)} s)") }
            advice.rejoinAhead?.let { append(if (advice.rejoinBehind != null) ", ahead of " else " ahead of ").append("${it.code} (${Fmt.oneDecimal(advice.rejoinAheadGapMs / 1000f)} s)") }
        }
        Text(rejoin, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(6.dp))
        advice.carAhead?.let {
            Text(
                if (advice.undercutInRange) "Undercut on ${it.code}: in range" else "Undercut on ${it.code}: out of range",
                color = if (advice.undercutInRange) colors.personalBest else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        advice.carBehind?.let {
            Text(
                if (advice.undercutThreat) "Undercut threat from ${it.code}" else "${it.code} behind: no undercut threat",
                color = if (advice.undercutThreat) colors.warning else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = modifier.semantics { heading() })
}
