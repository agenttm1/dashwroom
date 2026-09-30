package com.dashwroom.f1telemetry.ui.screens.car

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dashwroom.f1telemetry.core.model.PlayerCarState
import com.dashwroom.f1telemetry.core.model.TyreSetState
import com.dashwroom.f1telemetry.core.model.WheelState
import com.dashwroom.f1telemetry.core.spec.ActualCompound
import com.dashwroom.f1telemetry.ui.adaptive.PaneLayoutInfo
import com.dashwroom.f1telemetry.ui.adaptive.PaneMode
import com.dashwroom.f1telemetry.ui.adaptive.TwoPane
import com.dashwroom.f1telemetry.ui.adaptive.rememberPaneLayout
import com.dashwroom.f1telemetry.ui.components.CarDiagram
import com.dashwroom.f1telemetry.ui.components.CarPartColors
import com.dashwroom.f1telemetry.ui.components.DashCard
import com.dashwroom.f1telemetry.ui.components.MetricTile
import com.dashwroom.f1telemetry.ui.components.Palette
import com.dashwroom.f1telemetry.ui.components.TyreBadge
import com.dashwroom.f1telemetry.ui.components.WaitingForTelemetry
import com.dashwroom.f1telemetry.ui.components.WheelNames
import com.dashwroom.f1telemetry.ui.components.compoundName
import com.dashwroom.f1telemetry.ui.components.ersModeName
import com.dashwroom.f1telemetry.ui.components.fuelMixName
import com.dashwroom.f1telemetry.ui.format.DisplayPrefs
import com.dashwroom.f1telemetry.ui.format.Fmt
import com.dashwroom.f1telemetry.ui.format.LocalDisplayPrefs
import com.dashwroom.f1telemetry.ui.preview.PreviewData
import com.dashwroom.f1telemetry.ui.screens.overview.DamageLine
import com.dashwroom.f1telemetry.ui.theme.DashTheme
import com.dashwroom.f1telemetry.ui.theme.DashwroomTheme

@Composable
fun CarScreen(onOpenConnect: () -> Unit, viewModel: CarViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CarContent(state, onOpenConnect)
}

/**
 * Tyres (surface + carcass heat map on the car, pressures, brakes, wear) on one side; damage,
 * power-unit wear, tyre-set inventory and car systems on the other. One column on phones.
 */
@Composable
fun CarContent(
    state: CarUiState,
    onOpenConnect: () -> Unit,
    modifier: Modifier = Modifier,
    layout: PaneLayoutInfo = rememberPaneLayout(),
) {
    if (!state.hasData) {
        WaitingForTelemetry(
            title = "Waiting for car data",
            message = "Tyre temperatures, wear and damage appear once F1 25 sends car telemetry. Other cars' data needs their telemetry set to public.",
            actionLabel = "Connection setup",
            onAction = onOpenConnect,
            modifier = modifier,
        )
        return
    }
    val car = state.car
    if (layout.mode == PaneMode.SIDE_BY_SIDE) {
        TwoPane(
            first = { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) { TyresPanel(car) } },
            second = {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 12.dp, end = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SystemsCard(car)
                    DamageCard(car)
                    TyreSetsCard(car)
                }
            },
            modifier = modifier.fillMaxSize(),
            firstWeight = 0.52f,
            hinge = layout.hinge,
        )
    } else {
        Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TyresPanel(car)
            SystemsCard(car)
            DamageCard(car)
            TyreSetsCard(car)
        }
    }
}

@Composable
private fun TyresPanel(car: PlayerCarState) {
    val prefs = LocalDisplayPrefs.current
    DashCard(title = "Tyres & brakes") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TyreBadge(car.tyreVisual, size = 30.dp)
            Column(Modifier.padding(start = 10.dp)) {
                Text("${compoundName(car.tyreVisual)} · ${ActualCompound.name(car.tyreActual)}", style = MaterialTheme.typography.titleMedium)
                Text("${car.tyreAgeLaps} laps on this set", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(12.dp))
        if (car.wheels.size < 4) {
            Text("No tyre data yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@DashCard
        }
        val w = car.wheels
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(48.dp)) {
                WheelInfo(PlayerCarState.FRONT_LEFT, w[PlayerCarState.FRONT_LEFT], prefs, Alignment.End)
                WheelInfo(PlayerCarState.REAR_LEFT, w[PlayerCarState.REAR_LEFT], prefs, Alignment.End)
            }
            CarDiagram(
                CarPartColors.fromDamage(car.damage, car.wheels, MaterialTheme.colorScheme.outline),
                Modifier.weight(0.9f).aspectRatio(100f / 220f).padding(horizontal = 8.dp),
                description = "Tyre temperature heat map",
                wheelOverlay = { i, rect ->
                    val wheel = w[i]
                    val r = CornerRadius(rect.width * 0.25f)
                    // Outer band = surface temperature, inner block = carcass temperature,
                    // centre dot = brake temperature.
                    drawRoundRect(Palette.tyreTemp(wheel.surfaceTempC), rect.topLeft, rect.size, r)
                    val insetX = rect.width * 0.22f
                    val insetY = rect.height * 0.14f
                    drawRoundRect(
                        Palette.tyreTemp(wheel.innerTempC),
                        Offset(rect.left + insetX, rect.top + insetY),
                        Size(rect.width - insetX * 2, rect.height - insetY * 2),
                        CornerRadius(rect.width * 0.15f),
                    )
                    drawCircle(Palette.brakeTemp(wheel.brakeTempC), radius = rect.width * 0.16f, center = rect.center)
                    drawCircle(Color.Black.copy(alpha = 0.5f), radius = rect.width * 0.16f, center = rect.center, style = Stroke(1.dp.toPx()))
                    drawRoundRect(Color.Black.copy(alpha = 0.6f), rect.topLeft, rect.size, r, style = Stroke(1.5.dp.toPx()))
                },
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(48.dp)) {
                WheelInfo(PlayerCarState.FRONT_RIGHT, w[PlayerCarState.FRONT_RIGHT], prefs, Alignment.Start)
                WheelInfo(PlayerCarState.REAR_RIGHT, w[PlayerCarState.REAR_RIGHT], prefs, Alignment.Start)
            }
        }
        Spacer(Modifier.height(12.dp))
        Legend()
    }
}

@Composable
private fun WheelInfo(index: Int, w: WheelState, prefs: DisplayPrefs, align: Alignment.Horizontal) {
    val textAlign = if (align == Alignment.End) TextAlign.End else TextAlign.Start
    Column(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
            contentDescription = "${WheelNames[index]}: surface ${prefs.tempText(w.surfaceTempC)}, carcass ${prefs.tempText(w.innerTempC)}, " +
                "${Fmt.oneDecimal(w.pressurePsi)} psi, brake ${prefs.tempText(w.brakeTempC)}, wear ${w.wearPercent.toInt()} percent"
        },
        horizontalAlignment = align,
    ) {
        Text(WheelNames[index].uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(prefs.tempText(w.surfaceTempC), style = MaterialTheme.typography.headlineSmall, color = Palette.tyreTemp(w.surfaceTempC), textAlign = textAlign)
        Text("Carcass ${prefs.tempText(w.innerTempC)}", style = MaterialTheme.typography.bodyMedium, color = Palette.tyreTemp(w.innerTempC), textAlign = textAlign)
        Text("${Fmt.oneDecimal(w.pressurePsi)} psi", style = MaterialTheme.typography.bodyMedium, textAlign = textAlign)
        Text("Brake ${prefs.tempText(w.brakeTempC)}", style = MaterialTheme.typography.bodyMedium, color = Palette.brakeTemp(w.brakeTempC), textAlign = textAlign)
        Text("Wear ${w.wearPercent.toInt()}%", style = MaterialTheme.typography.titleMedium, color = Palette.damage(w.wearPercent), textAlign = textAlign)
        if (w.blistersPercent > 0) Text("Blisters ${w.blistersPercent}%", style = MaterialTheme.typography.bodySmall, color = Palette.damage(w.blistersPercent.toFloat()))
    }
}

@Composable
private fun Legend() {
    val prefs = LocalDisplayPrefs.current
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        for ((label, c) in listOf("Cold" to 60, "Optimal" to 95, "Hot" to 125)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.size(12.dp).background(Palette.tyreTemp(c), RoundedCornerShape(3.dp)))
                Text(" $label", style = MaterialTheme.typography.bodySmall)
            }
        }
        Text("Outer: surface · inner: carcass · dot: brake (${prefs.temperature.label})", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SystemsCard(car: PlayerCarState) {
    val prefs = LocalDisplayPrefs.current
    DashCard(title = "Car") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricTile("Engine", prefs.tempText(car.engineTemperatureC), Modifier.weight(1f))
            MetricTile("Brake bias", "${car.frontBrakeBias}%", Modifier.weight(1f))
            MetricTile("TC", if (car.tractionControl == 0) "Off" else car.tractionControl.toString(), Modifier.weight(1f))
            MetricTile("ABS", if (car.antiLockBrakes) "On" else "Off", Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricTile("Fuel", "${Fmt.oneDecimal(car.fuelInTankKg)} kg", Modifier.weight(1f))
            MetricTile("Mix", fuelMixName(car.fuelMix), Modifier.weight(1f))
            MetricTile("ERS", "${(car.ersStoreJ / PlayerCarState.ERS_MAX_J * 100).toInt()}%", Modifier.weight(1f))
            MetricTile("Deploy", ersModeName(car.ersDeployMode), Modifier.weight(1f))
        }
        car.ersHarvestLimitJ?.let {
            Spacer(Modifier.height(6.dp))
            Text("Harvest limit ${Fmt.megajoules(it)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (car.pitLimiterOn) Text("Pit limiter on", color = DashTheme.colors.warning, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun DamageCard(car: PlayerCarState) {
    val d = car.damage
    val colors = DashTheme.colors
    DashCard(title = "Damage & wear") {
        if (!car.hasDamage) {
            Text("No damage data yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@DashCard
        }
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("BODYWORK", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                DamageLine("Front wing L", d.frontLeftWing)
                DamageLine("Front wing R", d.frontRightWing)
                DamageLine("Rear wing", d.rearWing)
                DamageLine("Floor", d.floor)
                DamageLine("Diffuser", d.diffuser)
                DamageLine("Sidepods", d.sidepod)
                DamageLine("Gearbox", d.gearbox)
                for (i in 0..3) DamageLine("Tyre ${WheelNames[i].lowercase()}", car.wheels.getOrNull(i)?.damagePercent ?: 0)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("POWER UNIT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                DamageLine("Engine", d.engine)
                DamageLine("ICE", d.iceWear)
                DamageLine("MGU-H", d.mguhWear)
                DamageLine("MGU-K", d.mgukWear)
                DamageLine("Energy store", d.esWear)
                DamageLine("Control electronics", d.ceWear)
                DamageLine("Turbocharger", d.tcWear)
                for (i in 0..3) DamageLine("Brake ${WheelNames[i].lowercase()}", car.wheels.getOrNull(i)?.brakeDamagePercent ?: 0)
            }
        }
        val faults = buildList {
            if (d.drsFault) add("DRS fault")
            if (d.ersFault) add("ERS fault")
            if (d.engineBlown) add("Engine blown")
            if (d.engineSeized) add("Engine seized")
        }
        if (faults.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(faults.joinToString(" · "), color = colors.danger, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun TyreSetsCard(car: PlayerCarState) {
    DashCard(title = "Tyre sets") {
        if (car.tyreSets.isEmpty()) {
            Text("No tyre set data yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@DashCard
        }
        Row(Modifier.padding(bottom = 4.dp)) {
            for ((label, weight) in listOf("SET" to 1.4f, "WEAR" to 1f, "LIFE" to 1f, "Δ LAP" to 1f)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(weight), textAlign = if (label == "SET") TextAlign.Start else TextAlign.End)
            }
        }
        for (set in car.tyreSets) TyreSetRow(set)
    }
}

@Composable
private fun TyreSetRow(set: TyreSetState) {
    val colors = DashTheme.colors
    val alpha = if (set.available || set.fitted) 1f else 0.4f
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (set.fitted) MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f) else Color.Transparent, RoundedCornerShape(6.dp))
            .padding(vertical = 3.dp, horizontal = 4.dp)
            .semantics(mergeDescendants = true) { },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1.4f), verticalAlignment = Alignment.CenterVertically) {
            TyreBadge(set.visualCompound, size = 20.dp)
            Text(
                " ${ActualCompound.name(set.actualCompound)}" + if (set.fitted) " · fitted" else if (!set.available) " · used" else "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            )
        }
        Text("${set.wearPercent}%", style = MaterialTheme.typography.bodyMedium, color = Palette.damage(set.wearPercent.toFloat()).copy(alpha = alpha), modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        Text("${set.usableLifeLaps}/${set.lifeSpanLaps}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        Text(
            if (set.lapDeltaTimeMs == 0) "—" else Fmt.delta(set.lapDeltaTimeMs),
            style = MaterialTheme.typography.bodyMedium,
            color = if (set.lapDeltaTimeMs < 0) colors.personalBest else if (set.lapDeltaTimeMs > 0) colors.slower else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End,
        )
    }
}

@PreviewScreenSizes
@Preview(name = "Tablet", device = Devices.TABLET)
@Composable
private fun CarPreview() {
    DashwroomTheme { CarContent(CarUiState(true, PreviewData.info(), PreviewData.car()), onOpenConnect = {}) }
}
