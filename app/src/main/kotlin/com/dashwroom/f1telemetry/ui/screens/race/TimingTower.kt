package com.dashwroom.f1telemetry.ui.screens.race

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dashwroom.f1telemetry.core.model.DriverState
import com.dashwroom.f1telemetry.core.model.PitStatus
import com.dashwroom.f1telemetry.core.model.ResultStatus
import com.dashwroom.f1telemetry.core.model.SessionBests
import com.dashwroom.f1telemetry.ui.components.TeamStripe
import com.dashwroom.f1telemetry.ui.components.TyreBadge
import com.dashwroom.f1telemetry.ui.format.Fmt
import com.dashwroom.f1telemetry.ui.theme.DashColors
import com.dashwroom.f1telemetry.ui.theme.DashTheme
import com.dashwroom.f1telemetry.ui.theme.TeamColors
import kotlinx.collections.immutable.ImmutableList

/** Which optional columns fit the available width. */
@Immutable
data class TowerColumns(val gap: Boolean, val lastLap: Boolean, val bestLap: Boolean, val stops: Boolean) {
    companion object {
        fun forWidth(width: Dp) = TowerColumns(
            gap = width >= 400.dp,
            lastLap = width >= 480.dp,
            bestLap = width >= 640.dp,
            stops = width >= 560.dp,
        )
    }
}

/**
 * The race timing tower. Rows are keyed by vehicle index so position changes animate as moves,
 * not as content swaps, and each row only recomposes when its own [DriverState] changes.
 */
@Composable
fun TimingTower(
    drivers: ImmutableList<DriverState>,
    bests: SessionBests,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val columns = TowerColumns.forWidth(maxWidth)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
            item(key = "header", contentType = "header") { TowerHeader(columns) }
            items(drivers, key = { it.vehicleIndex }, contentType = { "driver" }) { d ->
                TowerRow(
                    d = d,
                    bests = bests,
                    columns = columns,
                    selected = d.vehicleIndex == selected,
                    onClick = { onSelect(d.vehicleIndex) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

@Composable
private fun TowerHeader(columns: TowerColumns) {
    val style = MaterialTheme.typography.labelSmall
    val c = MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("POS", style = style, color = c, modifier = Modifier.width(34.dp))
        Text("DRIVER", style = style, color = c, modifier = Modifier.width(62.dp))
        Text("TYRE", style = style, color = c, modifier = Modifier.width(56.dp))
        Text("INT", style = style, color = c, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        if (columns.gap) Text("GAP", style = style, color = c, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        if (columns.lastLap) Text("LAST", style = style, color = c, modifier = Modifier.weight(1.2f), textAlign = TextAlign.End)
        if (columns.bestLap) Text("BEST", style = style, color = c, modifier = Modifier.weight(1.2f), textAlign = TextAlign.End)
        if (columns.stops) Text("PIT", style = style, color = c, modifier = Modifier.width(34.dp), textAlign = TextAlign.End)
        Spacer(Modifier.width(78.dp))
    }
}

@Composable
private fun TowerRow(
    d: DriverState,
    bests: SessionBests,
    columns: TowerColumns,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DashTheme.colors
    val scheme = MaterialTheme.colorScheme
    val out = !d.isRunning && d.resultStatus != ResultStatus.FINISHED
    val bg = when {
        d.isPlayer -> scheme.primaryContainer.copy(alpha = 0.45f)
        else -> Color.Transparent
    }
    val numberStyle = MaterialTheme.typography.bodyLarge
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .padding(vertical = 1.dp)
            .background(bg, RoundedCornerShape(8.dp))
            .then(if (selected) Modifier.border(BorderStroke(2.dp, scheme.secondary), RoundedCornerShape(8.dp)) else Modifier)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                this.selected = selected
                contentDescription = describe(d)
            }
            .alpha(if (out) 0.5f else 1f)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (d.position > 0) d.position.toString() else "—",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.width(26.dp),
            textAlign = TextAlign.End,
        )
        Spacer(Modifier.width(8.dp))
        TeamStripe(TeamColors.of(d.teamId, d.liveryColour))
        Text(
            d.code,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (d.isPlayer) FontWeight.Bold else FontWeight.SemiBold,
            modifier = Modifier.padding(start = 6.dp).width(52.dp),
            maxLines = 1,
        )
        TyreBadge(d.tyreVisual, size = 22.dp)
        Text(
            d.tyreAgeLaps.toString(),
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp).width(30.dp),
        )
        Text(intervalText(d), style = numberStyle, modifier = Modifier.weight(1f), textAlign = TextAlign.End, maxLines = 1)
        if (columns.gap) Text(if (out) "" else Fmt.toLeader(d), style = numberStyle, color = scheme.onSurfaceVariant, modifier = Modifier.weight(1f), textAlign = TextAlign.End, maxLines = 1)
        if (columns.lastLap) {
            Text(
                Fmt.lapTime(d.lastLapTimeMs),
                style = numberStyle,
                color = lastLapColor(d, bests, colors) ?: scheme.onSurface,
                modifier = Modifier.weight(1.2f),
                textAlign = TextAlign.End,
                maxLines = 1,
            )
        }
        if (columns.bestLap) {
            Text(
                Fmt.lapTime(d.bestLapTimeMs),
                style = numberStyle,
                color = if (d.bestLapTimeMs > 0 && bests.lapVehicle == d.vehicleIndex) colors.sessionBest else scheme.onSurfaceVariant,
                modifier = Modifier.weight(1.2f),
                textAlign = TextAlign.End,
                maxLines = 1,
            )
        }
        if (columns.stops) Text(d.numPitStops.toString(), style = numberStyle, modifier = Modifier.width(34.dp), textAlign = TextAlign.End)
        StatusFlags(d, Modifier.width(78.dp).padding(start = 6.dp))
    }
}

@Composable
private fun StatusFlags(d: DriverState, modifier: Modifier) {
    val colors = DashTheme.colors
    Row(modifier.clearAndSetSemantics { }, horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        when (d.pitStatus) {
            PitStatus.PITTING -> Tag("PIT", colors.warning)
            PitStatus.IN_PIT_AREA -> Tag("BOX", colors.warning)
            PitStatus.NONE -> Unit
        }
        if (d.penaltiesSeconds > 0) Tag("+${d.penaltiesSeconds}", colors.danger)
        if (d.unservedDriveThroughs + d.unservedStopGoes > 0) Tag("DT", colors.danger)
        if (d.drsOpen) Tag("DRS", colors.drs) else if (d.drsAllowed) Tag("DRS", colors.drs.copy(alpha = 0.35f))
        if (d.overtakeActive) Tag("OT", colors.sessionBest)
    }
}

@Composable
private fun Tag(text: String, color: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = Color.Black,
        modifier = Modifier.background(color, RoundedCornerShape(4.dp)).padding(horizontal = 3.dp, vertical = 1.dp),
        maxLines = 1,
    )
}

private fun intervalText(d: DriverState): String = when (d.resultStatus) {
    ResultStatus.DID_NOT_FINISH -> "DNF"
    ResultStatus.RETIRED -> "RET"
    ResultStatus.DISQUALIFIED -> "DSQ"
    ResultStatus.NOT_CLASSIFIED -> "NC"
    ResultStatus.INACTIVE, ResultStatus.INVALID -> "—"
    ResultStatus.FINISHED, ResultStatus.ACTIVE -> when {
        d.position == 1 -> "Leader"
        d.pitStatus != PitStatus.NONE -> "In pit"
        else -> Fmt.interval(d, leader = false)
    }
}

fun lastLapColor(d: DriverState, bests: SessionBests, colors: DashColors): Color? = when {
    d.lastLapTimeMs <= 0 -> null
    bests.lapMs > 0 && d.lastLapTimeMs <= bests.lapMs -> colors.sessionBest
    d.bestLapTimeMs > 0 && d.lastLapTimeMs <= d.bestLapTimeMs -> colors.personalBest
    else -> null
}

private fun describe(d: DriverState): String = buildString {
    append("Position ${d.position}, ${d.name}")
    if (d.isPlayer) append(" (you)")
    append(", ${intervalText(d)}")
    if (d.lastLapTimeMs > 0) append(", last lap ${Fmt.lapTime(d.lastLapTimeMs)}")
    append(", tyres ${d.tyreAgeLaps} laps")
    if (d.pitStatus != PitStatus.NONE) append(", in the pits")
    if (d.penaltiesSeconds > 0) append(", ${d.penaltiesSeconds} second penalty")
}
