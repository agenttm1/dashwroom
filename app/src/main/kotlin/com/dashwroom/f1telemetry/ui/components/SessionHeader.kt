package com.dashwroom.f1telemetry.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dashwroom.f1telemetry.core.model.SessionInfo
import com.dashwroom.f1telemetry.ui.format.Fmt
import com.dashwroom.f1telemetry.ui.format.LocalDisplayPrefs
import com.dashwroom.f1telemetry.ui.theme.DashTheme

/** Track · session · lap or clock · weather · temperatures · safety car — one line on tablets, wraps on phones. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SessionHeader(info: SessionInfo?, leaderLap: Int, modifier: Modifier = Modifier) {
    val prefs = LocalDisplayPrefs.current
    Surface(modifier, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        if (info == null) {
            Text("Waiting for session data…", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Surface
        }
        FlowRow(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(info.trackName, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                Text(info.sessionTypeName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (info.isRace && info.totalLaps > 0) {
                HeaderValue("Lap", "${leaderLap.coerceIn(0, info.totalLaps)} / ${info.totalLaps}")
            }
            if (!info.isRace || info.totalLaps == 0) {
                HeaderValue("Remaining", Fmt.clock(info.sessionTimeLeftS))
            }
            HeaderValue("Weather", info.weatherName)
            HeaderValue("Track", prefs.tempText(info.trackTemperatureC))
            HeaderValue("Air", prefs.tempText(info.airTemperatureC))
            SafetyCarBadge(info.safetyCarStatus)
            if (info.gamePaused) Badge("PAUSED", MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun HeaderValue(label: String, value: String) {
    Column {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun SafetyCarBadge(status: Int) {
    val colors = DashTheme.colors
    when (status) {
        1 -> Badge("SAFETY CAR", colors.warning)
        2 -> Badge("VIRTUAL SC", colors.warning)
        3 -> Badge("FORMATION LAP", colors.searching)
    }
}

@Composable
fun Badge(text: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier.background(color, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color.Black)
    }
}

fun ersModeName(mode: Int): String = when (mode) {
    0 -> "None"
    1 -> "Medium"
    2 -> "Hotlap"
    3 -> "Overtake"
    else -> "—"
}

fun fuelMixName(mix: Int): String = when (mix) {
    0 -> "Lean"
    1 -> "Standard"
    2 -> "Rich"
    3 -> "Max"
    else -> "—"
}
