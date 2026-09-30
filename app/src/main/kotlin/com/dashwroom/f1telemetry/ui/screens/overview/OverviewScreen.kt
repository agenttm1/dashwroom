package com.dashwroom.f1telemetry.ui.screens.overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.dashwroom.f1telemetry.core.model.DamageState
import com.dashwroom.f1telemetry.core.model.DriverState
import com.dashwroom.f1telemetry.core.model.PlayerCarState
import com.dashwroom.f1telemetry.core.model.SessionBests
import com.dashwroom.f1telemetry.core.state.HotTelemetry
import com.dashwroom.f1telemetry.ui.components.CarDiagram
import com.dashwroom.f1telemetry.ui.components.CarPartColors
import com.dashwroom.f1telemetry.ui.components.DashCard
import com.dashwroom.f1telemetry.ui.components.MetricTile
import com.dashwroom.f1telemetry.ui.components.Palette
import com.dashwroom.f1telemetry.ui.components.SessionHeader
import com.dashwroom.f1telemetry.ui.components.TeamStripe
import com.dashwroom.f1telemetry.ui.components.TrackMap
import com.dashwroom.f1telemetry.ui.components.TyreBadge
import com.dashwroom.f1telemetry.ui.components.WaitingForTelemetry
import com.dashwroom.f1telemetry.ui.components.WheelGridOrder
import com.dashwroom.f1telemetry.ui.components.WheelShort
import com.dashwroom.f1telemetry.ui.components.ersModeName
import com.dashwroom.f1telemetry.ui.components.fuelMixName
import com.dashwroom.f1telemetry.ui.format.Fmt
import com.dashwroom.f1telemetry.ui.format.LocalDisplayPrefs
import com.dashwroom.f1telemetry.ui.hot.HotDeltaBar
import com.dashwroom.f1telemetry.ui.hot.HotErsBar
import com.dashwroom.f1telemetry.ui.hot.rememberHotFrame
import com.dashwroom.f1telemetry.ui.preview.PreviewData
import com.dashwroom.f1telemetry.ui.theme.DashTheme
import com.dashwroom.f1telemetry.ui.theme.DashwroomTheme
import com.dashwroom.f1telemetry.ui.theme.TeamColors

@Composable
fun OverviewScreen(onOpenConnect: () -> Unit, viewModel: OverviewViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val frame = rememberHotFrame(viewModel.hot)
    val mapFrame = rememberHotFrame(viewModel.hot, everyFrame = true)
    OverviewContent(state, viewModel.hot, frame, mapFrame, onOpenConnect)
}

/**
 * Glanceable summary. Cards flow into as many columns as fit (≥ 300 dp each) so the same content
 * serves phones (one column), foldables and tablets (two to four).
 */
@Composable
fun OverviewContent(
    state: OverviewUiState,
    hot: HotTelemetry,
    frame: State<Long>,
    mapFrame: State<Long>,
    onOpenConnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!state.hasData) {
        WaitingForTelemetry(
            title = "Waiting for telemetry",
            message = "Start a session in F1 25 with UDP telemetry pointed at this device. The overview fills in as soon as packets arrive.",
            actionLabel = "Connection setup",
            onAction = onOpenConnect,
            modifier = modifier,
        )
        return
    }
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Adaptive(300.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalItemSpacing = 12.dp,
    ) {
        item(key = "header", span = StaggeredGridItemSpan.FullLine) {
            SessionHeader(state.info, state.race.leaderLap)
        }
        item(key = "position") { PositionCard(state.player, state.ahead, state.behind, state.race.bests) }
        item(key = "delta") {
            DashCard(title = "Live delta · ${LocalDisplayPrefs.current.deltaReference.label}") {
                HotDeltaBar(hot, frame, LocalDisplayPrefs.current.deltaReference)
            }
        }
        item(key = "map") {
            DashCard(title = "Track") {
                TrackMap(state.outline, hot, mapFrame, state.mapCars, Modifier.fillMaxWidth().aspectRatio(1.25f))
            }
        }
        item(key = "tyres") { TyresCard(state.car) }
        item(key = "fuel") { FuelCard(state.car) }
        item(key = "ers") {
            DashCard(title = "ERS") {
                HotErsBar(hot, frame)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    MetricTile("Mode", ersModeName(state.car.ersDeployMode), Modifier.weight(1f))
                    MetricTile("Harvested", Fmt.megajoules(state.car.ersHarvestedThisLapJ), Modifier.weight(1f))
                    MetricTile("Deployed", Fmt.megajoules(state.car.ersDeployedThisLapJ), Modifier.weight(1f))
                }
            }
        }
        item(key = "damage") { DamageCard(state.car) }
    }
}

@Composable
private fun PositionCard(player: DriverState?, ahead: DriverState?, behind: DriverState?, bests: SessionBests) {
    val colors = DashTheme.colors
    DashCard(title = "Position") {
        if (player == null) {
            Text("Waiting for lap data…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@DashCard
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "P${player.position}",
                fontSize = 56.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { contentDescription = "Position ${player.position}" },
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                GapLine("Ahead", ahead, if (ahead != null) Fmt.gap(player.intervalMs) else Fmt.NONE)
                GapLine("Behind", behind, if (behind != null) Fmt.gap(behind.intervalMs) else Fmt.NONE)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            val bestColor = when {
                player.bestLapTimeMs <= 0 -> null
                bests.lapVehicle == player.vehicleIndex -> colors.sessionBest
                else -> colors.personalBest
            }
            val lastColor = when {
                player.lastLapTimeMs <= 0 -> null
                bests.lapMs > 0 && player.lastLapTimeMs <= bests.lapMs -> colors.sessionBest
                player.lastLapTimeMs <= player.bestLapTimeMs -> colors.personalBest
                else -> colors.slower
            }
            MetricTile("Last lap", Fmt.lapTime(player.lastLapTimeMs), Modifier.weight(1f), lastColor)
            MetricTile("Best lap", Fmt.lapTime(player.bestLapTimeMs), Modifier.weight(1f), bestColor)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            MetricTile("Leader", Fmt.toLeader(player), Modifier.weight(1f))
            MetricTile("Stops", player.numPitStops.toString(), Modifier.weight(1f))
            MetricTile(
                "Penalties",
                if (player.penaltiesSeconds > 0) "+${player.penaltiesSeconds}s" else "—",
                Modifier.weight(1f),
                if (player.penaltiesSeconds > 0) colors.danger else null,
            )
        }
    }
}

@Composable
private fun GapLine(label: String, driver: DriverState?, gap: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(56.dp))
        if (driver != null) {
            TeamStripe(TeamColors.of(driver.teamId, driver.liveryColour))
            Text(driver.code, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 6.dp).weight(1f))
        } else {
            Spacer(Modifier.weight(1f))
        }
        Text(gap, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun TyresCard(car: PlayerCarState) {
    val prefs = LocalDisplayPrefs.current
    DashCard(title = "Tyres") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TyreBadge(car.tyreVisual, size = 30.dp)
            Spacer(Modifier.width(10.dp))
            Text("${car.tyreAgeLaps} laps old", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(10.dp))
        if (car.wheels.size < 4) {
            Text("No tyre data yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@DashCard
        }
        for (row in 0..1) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                for (col in 0..1) {
                    val i = WheelGridOrder[row * 2 + col]
                    val w = car.wheels[i]
                    Column(Modifier.weight(1f)) {
                        Text(WheelShort[i], style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("${w.wearPercent.toInt()}%", style = MaterialTheme.typography.titleLarge, color = Palette.damage(w.wearPercent))
                            Spacer(Modifier.width(8.dp))
                            Text(prefs.tempText(w.surfaceTempC), style = MaterialTheme.typography.bodyLarge, color = Palette.tyreTemp(w.surfaceTempC))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FuelCard(car: PlayerCarState) {
    val colors = DashTheme.colors
    DashCard(title = "Fuel") {
        val frac = if (car.fuelCapacityKg > 0) (car.fuelInTankKg / car.fuelCapacityKg).coerceIn(0f, 1f) else 0f
        LinearProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxWidth().height(8.dp), drawStopIndicator = {})
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            MetricTile("In tank", "${Fmt.oneDecimal(car.fuelInTankKg)} kg", Modifier.weight(1f))
            MetricTile(
                "Margin",
                "${Fmt.signedOneDecimal(car.fuelRemainingLaps)} laps",
                Modifier.weight(1f),
                when {
                    car.fuelRemainingLaps < -0.2f -> colors.danger
                    car.fuelRemainingLaps < 0.3f -> colors.warning
                    else -> colors.personalBest
                },
            )
            MetricTile("Mix", fuelMixName(car.fuelMix), Modifier.weight(1f))
        }
    }
}

@Composable
private fun DamageCard(car: PlayerCarState) {
    val d: DamageState = car.damage
    DashCard(title = "Damage") {
        if (!car.hasDamage) {
            Text("No damage data yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@DashCard
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            CarDiagram(
                CarPartColors.fromDamage(d, car.wheels, MaterialTheme.colorScheme.outline),
                Modifier.weight(0.8f).heightIn(min = 160.dp).aspectRatio(100f / 220f),
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1.2f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                DamageLine("Front wing L", d.frontLeftWing)
                DamageLine("Front wing R", d.frontRightWing)
                DamageLine("Rear wing", d.rearWing)
                DamageLine("Floor", d.floor)
                DamageLine("Diffuser", d.diffuser)
                DamageLine("Sidepods", d.sidepod)
                DamageLine("Gearbox", d.gearbox)
                DamageLine("Engine", d.engine)
                if (d.drsFault) Text("DRS fault", color = DashTheme.colors.danger, style = MaterialTheme.typography.labelLarge)
                if (d.ersFault) Text("ERS fault", color = DashTheme.colors.danger, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
fun DamageLine(label: String, percent: Int) {
    Row {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text("$percent%", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = Palette.damage(percent.toFloat()))
    }
}

@PreviewScreenSizes
@Preview(name = "Tablet", device = Devices.TABLET)
@Composable
private fun OverviewPreview() {
    DashwroomTheme {
        val hot = PreviewData.hot()
        val frame = PreviewData.frame()
        OverviewContent(PreviewData.overview(), hot, frame, frame, onOpenConnect = {})
    }
}
