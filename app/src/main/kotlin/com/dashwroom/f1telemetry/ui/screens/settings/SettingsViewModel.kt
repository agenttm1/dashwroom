package com.dashwroom.f1telemetry.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dashwroom.f1telemetry.core.model.SourceKind
import com.dashwroom.f1telemetry.core.protocol.PacketFormat
import com.dashwroom.f1telemetry.data.recording.RecordingFile
import com.dashwroom.f1telemetry.data.recording.RecordingManager
import com.dashwroom.f1telemetry.data.settings.AppSettings
import com.dashwroom.f1telemetry.data.settings.DeltaReference
import com.dashwroom.f1telemetry.data.settings.OrientationLock
import com.dashwroom.f1telemetry.data.settings.ScreenKey
import com.dashwroom.f1telemetry.data.settings.SettingsRepository
import com.dashwroom.f1telemetry.data.settings.SpeedUnit
import com.dashwroom.f1telemetry.data.settings.TemperatureUnit
import com.dashwroom.f1telemetry.data.settings.ThemeMode
import com.dashwroom.f1telemetry.data.settings.UiDensity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val recordings: ImmutableList<RecordingFile> = persistentListOf(),
    val loaded: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val recordings: RecordingManager,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(repository.settings, recordings.recordings) { s, r ->
        SettingsUiState(s, r.toImmutableList(), loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    private fun update(block: suspend SettingsRepository.() -> Unit) {
        viewModelScope.launch { repository.block() }
    }

    fun setPort(port: Int) = update { setUdpPort(port) }
    fun setSource(source: SourceKind) = update { setDataSource(source) }
    fun setMockFormat(format: PacketFormat) = update { setMockFormat(format) }
    fun setReplayFile(path: String?) = update { setReplayFile(path) }
    fun setTheme(mode: ThemeMode) = update { setThemeMode(mode) }
    fun setSpeedUnit(unit: SpeedUnit) = update { setSpeedUnit(unit) }
    fun setTemperatureUnit(unit: TemperatureUnit) = update { setTemperatureUnit(unit) }
    fun setDensity(density: UiDensity) = update { setUiDensity(density) }
    fun setKeepScreenOn(enabled: Boolean) = update { setKeepScreenOn(enabled) }
    fun setStartOnBoot(enabled: Boolean) = update { setStartOnBoot(enabled) }
    fun setDeltaReference(reference: DeltaReference) = update { setDeltaReference(reference) }
    fun setDebugHud(enabled: Boolean) = update { setDebugHud(enabled) }
    fun setOrientation(screen: ScreenKey, lock: OrientationLock) = update { setOrientationLock(screen, lock) }
    fun deleteRecording(path: String) = recordings.delete(path)
}
