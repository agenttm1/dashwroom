package com.dashwroom.f1telemetry.ui.screens.connect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dashwroom.f1telemetry.core.TelemetryRepository
import com.dashwroom.f1telemetry.core.model.ConnectionState
import com.dashwroom.f1telemetry.core.model.SourceKind
import com.dashwroom.f1telemetry.core.model.TelemetryStatus
import com.dashwroom.f1telemetry.data.network.NetworkInfo
import com.dashwroom.f1telemetry.data.network.NetworkMonitor
import com.dashwroom.f1telemetry.data.network.WifiBand
import com.dashwroom.f1telemetry.data.recording.RecordingManager
import com.dashwroom.f1telemetry.data.recording.RecordingState
import com.dashwroom.f1telemetry.data.settings.AppSettings
import com.dashwroom.f1telemetry.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import kotlin.math.roundToInt

data class ConnectUiState(
    val status: TelemetryStatus = TelemetryStatus(),
    val network: NetworkInfo = NetworkInfo(),
    val port: Int = AppSettings.DEFAULT_PORT,
    val source: SourceKind = SourceKind.LIVE,
    val recording: RecordingState = RecordingState(),
    val warnings: ImmutableList<ConnectWarning> = persistentListOf(),
)

/** Actionable problems, most important first. */
sealed interface ConnectWarning {
    val message: String

    data class SourceError(override val message: String) : ConnectWarning
    data object NoWifi : ConnectWarning {
        override val message = "This phone isn't on Wi-Fi. Connect it to the same network as your PC or console."
    }
    data object SlowBand : ConnectWarning {
        override val message = "You're on 2.4 GHz Wi-Fi. Use a 5 GHz network for lower latency and less packet loss."
    }
    data class LowRate(val hz: Int) : ConnectWarning {
        override val message = "Receiving only $hz Hz. Set UDP Send Rate to 60 Hz in the game's telemetry settings."
    }
    data class UnsupportedFormat(val format: Int) : ConnectWarning {
        override val message = "The game is sending UDP format $format. Set UDP Format to 2025 (or 2026 with the Season Pack)."
    }
    data class HighLoss(val percent: Float) : ConnectWarning {
        override val message = "About ${"%.1f".format(percent)}% of packets are being lost. Move closer to the router or use 5 GHz."
    }
    data class SizeMismatch(val count: Long) : ConnectWarning {
        override val message = "$count packets had an unexpected size and were skipped — the game version may be newer than this app."
    }
}

@HiltViewModel
class ConnectViewModel @Inject constructor(
    repository: TelemetryRepository,
    network: NetworkMonitor,
    settings: SettingsRepository,
    private val recordings: RecordingManager,
) : ViewModel() {

    val uiState: StateFlow<ConnectUiState> = combine(
        repository.status, network.info, settings.settings, recordings.state,
    ) { status, net, s, rec ->
        ConnectUiState(
            status = status,
            network = net,
            port = s.udpPort,
            source = status.source ?: s.dataSource,
            recording = rec,
            warnings = warnings(status, net, status.source ?: s.dataSource),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConnectUiState())

    fun startRecording() = recordings.start()

    fun stopRecording() = recordings.stop()

    private fun warnings(status: TelemetryStatus, net: NetworkInfo, source: SourceKind): ImmutableList<ConnectWarning> {
        val list = mutableListOf<ConnectWarning>()
        status.sourceError?.let { list += ConnectWarning.SourceError(it) }
        if (source == SourceKind.LIVE) {
            if (!net.onWifi && net.primaryAddress == null) list += ConnectWarning.NoWifi
            if (net.band == WifiBand.GHZ_2_4) list += ConnectWarning.SlowBand
            if (status.connection == ConnectionState.CONNECTED && status.telemetryHz > 0f && status.telemetryHz < MIN_HEALTHY_HZ) {
                list += ConnectWarning.LowRate(status.telemetryHz.roundToInt())
            }
        }
        status.lastUnknownFormat?.let { if (status.unknownFormatPackets > 0) list += ConnectWarning.UnsupportedFormat(it) }
        status.lossPercent?.let { if (it >= HIGH_LOSS_PERCENT) list += ConnectWarning.HighLoss(it) }
        if (status.sizeMismatches > 0) list += ConnectWarning.SizeMismatch(status.sizeMismatches)
        return list.toImmutableList()
    }

    private companion object {
        const val MIN_HEALTHY_HZ = 55f
        const val HIGH_LOSS_PERCENT = 2f
    }
}
