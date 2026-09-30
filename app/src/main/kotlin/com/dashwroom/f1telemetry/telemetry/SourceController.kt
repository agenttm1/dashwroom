package com.dashwroom.f1telemetry.telemetry

import com.dashwroom.f1telemetry.core.TelemetryRepository
import com.dashwroom.f1telemetry.core.ingest.DatagramSink
import com.dashwroom.f1telemetry.core.model.SourceKind
import com.dashwroom.f1telemetry.core.protocol.PacketFormat
import com.dashwroom.f1telemetry.core.source.PacketSource
import com.dashwroom.f1telemetry.data.recording.RecordingManager
import com.dashwroom.f1telemetry.data.settings.SettingsRepository
import com.dashwroom.f1telemetry.di.ApplicationScope
import com.dashwroom.f1telemetry.replay.mock.MockSessionMode
import com.dashwroom.f1telemetry.replay.mock.MockTelemetryEmitter
import com.dashwroom.f1telemetry.replay.record.ReplaySource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** What the user selected (or a benchmark forced) as the telemetry source. */
data class SourceConfig(
    val kind: SourceKind,
    val port: Int,
    val mockFormat: PacketFormat,
    val mockSession: MockSessionMode,
    val replayFile: String?,
)

/**
 * Keeps [TelemetryRepository] running the source chosen in Settings (LIVE / MOCK / REPLAY),
 * switching whenever the choice changes. Started and stopped by [UdpTelemetryService].
 */
@Singleton
class SourceController @Inject constructor(
    private val repository: TelemetryRepository,
    private val settings: SettingsRepository,
    private val recordings: RecordingManager,
    @ApplicationScope private val scope: CoroutineScope,
) {
    /** Process-lifetime override, e.g. the macrobenchmark launching the app straight into MOCK. */
    private val override = MutableStateFlow<SourceKind?>(null)
    private var job: Job? = null

    fun overrideSource(kind: SourceKind?) {
        override.value = kind
    }

    @Synchronized
    fun start() {
        if (job != null) return
        job = scope.launch {
            combine(settings.settings, override) { s, forced ->
                SourceConfig(forced ?: s.dataSource, s.udpPort, s.mockFormat, s.mockSession, s.replayFile)
            }.distinctUntilChanged().collect { config -> repository.setSource(create(config)) }
        }
    }

    @Synchronized
    fun stop() {
        job?.cancel()
        job = null
        repository.setSource(null)
    }

    private suspend fun create(config: SourceConfig): PacketSource = when (config.kind) {
        SourceKind.LIVE -> OsUdpSource(config.port)
        SourceKind.MOCK -> MockTelemetryEmitter(config.mockFormat, rateHz = 60, mode = config.mockSession)
        SourceKind.REPLAY -> {
            val file = config.replayFile?.let(::File)?.takeIf { it.exists() } ?: recordings.latest()
            if (file == null) UnavailableSource(SourceKind.REPLAY, "Replay", "No recordings yet — record a session from the Connect screen first.")
            else ReplaySource(file, speed = 1f, loop = true)
        }
    }
}

/** A source that can't start; its message surfaces as the status error. */
private class UnavailableSource(
    override val kind: SourceKind,
    override val description: String,
    private val reason: String,
) : PacketSource {
    override suspend fun run(sink: DatagramSink) = throw IllegalStateException(reason)
}
