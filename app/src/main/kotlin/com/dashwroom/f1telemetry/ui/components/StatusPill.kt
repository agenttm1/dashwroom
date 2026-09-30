package com.dashwroom.f1telemetry.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dashwroom.f1telemetry.core.model.ConnectionState
import com.dashwroom.f1telemetry.core.model.SourceKind
import com.dashwroom.f1telemetry.core.model.TelemetryStatus
import com.dashwroom.f1telemetry.ui.theme.DashTheme
import kotlin.math.roundToInt

@Composable
fun connectionColor(state: ConnectionState): Color = when (state) {
    ConnectionState.CONNECTED -> DashTheme.colors.connected
    ConnectionState.STALE -> DashTheme.colors.stale
    ConnectionState.SEARCHING -> DashTheme.colors.searching
}

/** Compact connection indicator for the top bar: coloured dot + state + send rate. */
@Composable
fun StatusPill(status: TelemetryStatus, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val color by animateColorAsState(connectionColor(status.connection), label = "status")
    val label = buildString {
        append(status.connection.name)
        if (status.connection != ConnectionState.SEARCHING) append(" · ${status.telemetryHz.roundToInt()} Hz")
        if (status.source == SourceKind.MOCK) append(" · MOCK")
        if (status.source == SourceKind.REPLAY) append(" · REPLAY")
    }
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.semantics { contentDescription = "Telemetry $label. Open connection details." },
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(color, CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}
