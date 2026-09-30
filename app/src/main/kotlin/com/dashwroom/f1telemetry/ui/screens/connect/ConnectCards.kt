package com.dashwroom.f1telemetry.ui.screens.connect

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FiberManualRecord
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dashwroom.f1telemetry.core.model.ConnectionState
import com.dashwroom.f1telemetry.core.model.SourceKind
import com.dashwroom.f1telemetry.core.model.TelemetryStatus
import com.dashwroom.f1telemetry.data.recording.RecordingState
import com.dashwroom.f1telemetry.ui.components.DashCard
import com.dashwroom.f1telemetry.ui.components.MetricTile
import com.dashwroom.f1telemetry.ui.components.connectionColor
import com.dashwroom.f1telemetry.ui.theme.DashTheme
import com.dashwroom.f1telemetry.ui.theme.MonoNumbers
import kotlinx.collections.immutable.ImmutableList
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun StatusCard(state: ConnectUiState, modifier: Modifier = Modifier) {
    val status = state.status
    val color by animateColorAsState(connectionColor(status.connection), label = "state")
    DashCard(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(14.dp).background(color, CircleShape))
            Spacer(Modifier.width(10.dp))
            Text(
                status.connection.name,
                style = MaterialTheme.typography.headlineMedium,
                color = color,
                modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
            )
            SourceBadge(state.source)
        }
        Spacer(Modifier.height(4.dp))
        Text(connectionSubtitle(status), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(Modifier.height(20.dp))
        if (state.source == SourceKind.LIVE) {
            Text("THIS PHONE'S IP — ENTER IT IN THE GAME", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val ip = state.network.primaryAddress
            BasicText(
                text = ip ?: "No Wi-Fi address",
                style = MaterialTheme.typography.displayLarge.copy(
                    color = if (ip != null) MaterialTheme.colorScheme.onSurface else DashTheme.colors.warning,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 24.sp, maxFontSize = 76.sp),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Phone IP address ${ip ?: "unavailable"}" },
            )
            Text(
                "UDP port ${state.port}" + (state.network.band?.let { " · Wi-Fi ${it.label}" } ?: ""),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.network.otherAddresses.isNotEmpty()) {
                Text(
                    "Also reachable at " + state.network.otherAddresses.joinToString { "${it.address} (${it.interfaceName})" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Text(
                if (state.source == SourceKind.MOCK) "Mock race — no game needed" else "Replaying a recorded session",
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(status.sourceDescription, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun connectionSubtitle(status: TelemetryStatus): String = when (status.connection) {
    ConnectionState.CONNECTED -> buildString {
        append(status.game?.let { "${it.displayName} ${it.versionLabel}" } ?: "Receiving")
        status.sender?.takeIf { it != "mock" && it != "replay" }?.let { append(" · from $it") }
    }
    ConnectionState.STALE -> "No packets for ${(status.msSinceLastPacket ?: 0) / 1000.0} s — game paused or in a menu?"
    ConnectionState.SEARCHING -> if (status.totalPackets == 0L) "Listening for the game…" else "Lost the game — listening again…"
}

@Composable
private fun SourceBadge(source: SourceKind) {
    val (label, color) = when (source) {
        SourceKind.LIVE -> "LIVE" to MaterialTheme.colorScheme.secondary
        SourceKind.MOCK -> "MOCK" to DashTheme.colors.sessionBest
        SourceKind.REPLAY -> "REPLAY" to DashTheme.colors.warning
    }
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier = Modifier
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
fun WarningsCard(warnings: ImmutableList<ConnectWarning>, modifier: Modifier = Modifier) {
    if (warnings.isEmpty()) return
    DashCard(modifier.fillMaxWidth()) {
        warnings.forEachIndexed { i, w ->
            if (i > 0) Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Top) {
                val tint = if (w is ConnectWarning.SourceError) DashTheme.colors.danger else DashTheme.colors.warning
                Icon(Icons.Outlined.WarningAmber, contentDescription = "Warning", tint = tint, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(w.message, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun SetupStepsCard(address: String?, port: Int, source: SourceKind, modifier: Modifier = Modifier) {
    DashCard(modifier.fillMaxWidth(), title = "In-game setup") {
        if (source != SourceKind.LIVE) {
            Text(
                "You're on ${source.name.lowercase()} data. Switch Data source to Live in Settings to use the game.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
        }
        val steps = listOf(
            "Connect this phone to the same Wi-Fi as your PC / PS5 / Xbox (5 GHz recommended).",
            "In F1 25 open Settings › Telemetry Settings.",
            "UDP Telemetry: On",
            "UDP Broadcast Mode: Off",
            "UDP IP Address: ${address ?: "this phone's Wi-Fi IP"}",
            "UDP Port: $port",
            "UDP Send Rate: 60 Hz (the default is 20)",
            "UDP Format: 2025 — or 2026 on the 2026 Season Pack",
            "Optional: Your Telemetry: Public, so other players' cars show full data online.",
        )
        steps.forEachIndexed { i, step ->
            Row(Modifier.padding(vertical = 3.dp)) {
                Text("${i + 1}.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.widthIn(min = 24.dp))
                Text(step, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun DiagnosticsCard(status: TelemetryStatus, modifier: Modifier = Modifier) {
    DashCard(modifier.fillMaxWidth(), title = "Live diagnostics") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricTile("Packets/s", status.packetsPerSecond.roundToInt().toString())
            MetricTile(
                "Send rate",
                if (status.isReceiving) "${status.telemetryHz.roundToInt()} Hz" else "—",
                valueColor = if (status.isReceiving && status.telemetryHz < 55f) DashTheme.colors.warning else null,
            )
            MetricTile(
                "Loss (est.)",
                status.lossPercent?.let { String.format(Locale.US, "%.1f%%", it) } ?: "—",
                valueColor = status.lossPercent?.let { if (it >= 2f) DashTheme.colors.warning else DashTheme.colors.connected },
            )
            MetricTile("Bandwidth", formatRate(status.bytesPerSecond))
            MetricTile("Total", formatCount(status.totalPackets))
        }
        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(10.dp))
        val game = status.game
        InfoRow("Game", game?.let { "${it.displayName} · ${it.versionLabel}" } ?: "—")
        InfoRow("Packet format", game?.let { "${it.packetFormat} · game year ${it.gameYear}" } ?: "—")
        InfoRow("Session UID", status.sessionUid?.let { java.lang.Long.toUnsignedString(it) } ?: "—")
        InfoRow("Sender", status.sender ?: "—")
        InfoRow("Last packet", status.msSinceLastPacket?.let { if (it < 1000) "$it ms ago" else "${it / 1000} s ago" } ?: "never")
        InfoRow("Coalesced (stale, skipped)", formatCount(status.coalescedPackets))
        InfoRow("Out of order", formatCount(status.outOfOrderPackets))
        InfoRow("Unsupported format", formatCount(status.unknownFormatPackets) + (status.lastUnknownFormat?.let { " (last: $it)" } ?: ""))
        InfoRow("Size mismatches", formatCount(status.sizeMismatches), warn = status.sizeMismatches > 0)
    }
}

@Composable
private fun InfoRow(label: String, value: String, warn: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium.merge(MonoNumbers),
            color = if (warn) DashTheme.colors.warning else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun PacketTypesCard(status: TelemetryStatus, modifier: Modifier = Modifier) {
    DashCard(modifier.fillMaxWidth(), title = "Packets by type") {
        if (status.perType.isEmpty()) {
            Text("No packets yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@DashCard
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
            HeaderCell("ID", Modifier.width(32.dp))
            HeaderCell("Type", Modifier.weight(1f))
            HeaderCell("Rate", Modifier.width(64.dp))
            HeaderCell("Count", Modifier.width(80.dp))
            HeaderCell("Err", Modifier.width(40.dp))
        }
        status.perType.forEach { t ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Cell(if (t.packetId > 16) "?" else t.packetId.toString(), Modifier.width(32.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                Cell(t.name, Modifier.weight(1f))
                Cell(formatHz(t.perSecond), Modifier.width(64.dp))
                Cell(formatCount(t.count), Modifier.width(80.dp))
                Cell(
                    t.sizeMismatches.toString(), Modifier.width(40.dp),
                    if (t.sizeMismatches > 0) DashTheme.colors.warning else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun HeaderCell(text: String, modifier: Modifier) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

@Composable
private fun Cell(text: String, modifier: Modifier, color: Color = MaterialTheme.colorScheme.onSurface) {
    Text(text, style = MaterialTheme.typography.bodyMedium.merge(MonoNumbers), color = color, modifier = modifier, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
fun RecordingCard(
    recording: RecordingState,
    receiving: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DashCard(modifier.fillMaxWidth(), title = "Record session") {
        if (recording.active) {
            Text(recording.fileName.orEmpty(), style = MaterialTheme.typography.bodyMedium.merge(MonoNumbers))
            Text(
                "${formatCount(recording.packets)} packets · ${formatBytes(recording.bytes)}" +
                    if (recording.dropped > 0) " · ${recording.dropped} dropped" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            FilledTonalButton(onClick = onStop) {
                Icon(Icons.Outlined.Stop, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Stop recording")
            }
        } else {
            Text(
                "Captures raw packets to a .bin file you can replay later (Settings › Data source › Replay).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            FilledTonalButton(onClick = onStart, enabled = receiving) {
                Icon(Icons.Outlined.FiberManualRecord, contentDescription = null, tint = if (receiving) DashTheme.colors.danger else Color.Unspecified)
                Spacer(Modifier.width(6.dp))
                Text("Start recording")
            }
        }
    }
}

internal fun formatCount(n: Long): String = when {
    n >= 1_000_000 -> String.format(Locale.US, "%.1fM", n / 1e6)
    n >= 10_000 -> String.format(Locale.US, "%.1fk", n / 1e3)
    else -> n.toString()
}

internal fun formatHz(v: Float): String = if (v >= 10f) "${v.roundToInt()}/s" else String.format(Locale.US, "%.1f/s", v)

internal fun formatRate(bytesPerSecond: Float): String = when {
    bytesPerSecond >= 1_000_000f -> String.format(Locale.US, "%.1f MB/s", bytesPerSecond / 1e6f)
    bytesPerSecond >= 1_000f -> "${(bytesPerSecond / 1e3f).roundToInt()} kB/s"
    else -> "${bytesPerSecond.roundToInt()} B/s"
}

internal fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000 -> String.format(Locale.US, "%.1f MB", bytes / 1e6)
    bytes >= 1_000 -> "${bytes / 1_000} kB"
    else -> "$bytes B"
}
