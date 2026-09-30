package com.dashwroom.f1telemetry.ui.screens.connect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dashwroom.f1telemetry.core.model.ConnectionState
import com.dashwroom.f1telemetry.core.model.GameInfo
import com.dashwroom.f1telemetry.core.model.PacketTypeStats
import com.dashwroom.f1telemetry.core.model.SourceKind
import com.dashwroom.f1telemetry.core.model.TelemetryStatus
import com.dashwroom.f1telemetry.core.protocol.PacketFormat
import com.dashwroom.f1telemetry.data.network.NetworkInfo
import com.dashwroom.f1telemetry.ui.theme.DashwroomTheme
import kotlinx.collections.immutable.persistentListOf

@Composable
fun ConnectScreen(viewModel: ConnectViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ConnectContent(state, onStartRecording = viewModel::startRecording, onStopRecording = viewModel::stopRecording)
}

/**
 * Single column on phones; two panes (status + setup | diagnostics) from 600 dp, sized with
 * weights rather than fixed widths.
 */
@Composable
fun ConnectContent(
    state: ConnectUiState,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val twoPane = maxWidth >= 600.dp
        if (twoPane) {
            Row(
                Modifier.fillMaxSize().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    StatusCard(state)
                    WarningsCard(state.warnings)
                    SetupStepsCard(state.network.primaryAddress, state.port, state.source)
                }
                Column(
                    Modifier.weight(1.1f).verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    DiagnosticsCard(state.status)
                    PacketTypesCard(state.status)
                    RecordingCard(state.recording, state.status.isReceiving, onStartRecording, onStopRecording)
                }
            }
        } else {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                StatusCard(state)
                WarningsCard(state.warnings)
                if (!state.status.isReceiving) SetupStepsCard(state.network.primaryAddress, state.port, state.source)
                DiagnosticsCard(state.status)
                PacketTypesCard(state.status)
                RecordingCard(state.recording, state.status.isReceiving, onStartRecording, onStopRecording)
                if (state.status.isReceiving) SetupStepsCard(state.network.primaryAddress, state.port, state.source)
            }
        }
    }
}

private val previewConnected = ConnectUiState(
    status = TelemetryStatus(
        connection = ConnectionState.CONNECTED,
        source = SourceKind.LIVE,
        sourceDescription = "UDP port 20777",
        packetsPerSecond = 241f,
        telemetryHz = 60f,
        bytesPerSecond = 318_000f,
        lossPercent = 0.2f,
        game = GameInfo(2025, PacketFormat.F1_25, 25, 1, 18),
        sessionUid = 0x1234_5678_9ABCL,
        sender = "192.168.1.40",
        totalPackets = 48_210,
        msSinceLastPacket = 4,
        perType = persistentListOf(
            PacketTypeStats(0, "Motion", 12_010, 60f, 0),
            PacketTypeStats(1, "Session", 402, 2f, 0),
            PacketTypeStats(2, "Lap Data", 12_010, 60f, 0),
            PacketTypeStats(6, "Car Telemetry", 12_010, 60f, 0),
        ),
    ),
    network = NetworkInfo(primaryAddress = "192.168.1.23", onWifi = true, frequencyMhz = 5_180),
)

@PreviewScreenSizes
@Preview(name = "Tablet", device = Devices.TABLET)
@Composable
private fun ConnectConnectedPreview() {
    DashwroomTheme { ConnectContent(previewConnected, {}, {}) }
}

@Preview(name = "Searching", device = Devices.PIXEL_7)
@Composable
private fun ConnectSearchingPreview() {
    DashwroomTheme {
        ConnectContent(
            ConnectUiState(network = NetworkInfo(primaryAddress = "192.168.1.23", onWifi = true, frequencyMhz = 2_437), warnings = persistentListOf(ConnectWarning.SlowBand)),
            {}, {},
        )
    }
}
