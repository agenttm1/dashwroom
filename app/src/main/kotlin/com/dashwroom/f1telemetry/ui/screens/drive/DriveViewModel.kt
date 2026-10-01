package com.dashwroom.f1telemetry.ui.screens.drive

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dashwroom.f1telemetry.core.TelemetryRepository
import com.dashwroom.f1telemetry.core.model.DriverState
import com.dashwroom.f1telemetry.core.model.PlayerCarState
import com.dashwroom.f1telemetry.core.model.RaceState
import com.dashwroom.f1telemetry.core.model.SessionBests
import com.dashwroom.f1telemetry.core.model.SessionInfo
import com.dashwroom.f1telemetry.core.state.HotTelemetry
import com.dashwroom.f1telemetry.data.settings.DrivePreset
import com.dashwroom.f1telemetry.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class DriveUiState(
    val receiving: Boolean = false,
    /** What the user picked (may be AUTO). */
    val presetSetting: DrivePreset = DrivePreset.AUTO,
    val info: SessionInfo? = null,
    val player: DriverState? = null,
    val ahead: DriverState? = null,
    val behind: DriverState? = null,
    val bests: SessionBests = SessionBests(),
    val leaderLap: Int = 0,
    val numCars: Int = 0,
    val car: PlayerCarState = PlayerCarState(),
) {
    /** The preset actually shown: AUTO resolved from the session type. */
    val preset: DrivePreset get() = resolve(presetSetting, info)

    companion object {
        fun resolve(setting: DrivePreset, info: SessionInfo?): DrivePreset = when {
            setting != DrivePreset.AUTO -> setting
            info == null -> DrivePreset.RACE
            info.isTimeTrial -> DrivePreset.TIME_TRIAL
            info.isRace -> DrivePreset.RACE
            else -> DrivePreset.QUALI // qualifying and practice: lap-time focused
        }

        fun build(receiving: Boolean, preset: DrivePreset, info: SessionInfo?, race: RaceState, car: PlayerCarState): DriveUiState {
            val player = race.player
            val running = race.drivers.filter { it.isRunning }
            return DriveUiState(
                receiving = receiving,
                presetSetting = preset,
                info = info,
                player = player,
                ahead = player?.let { p -> running.firstOrNull { it.position == p.position - 1 } },
                behind = player?.let { p -> running.firstOrNull { it.position == p.position + 1 } },
                bests = race.bests,
                leaderLap = race.leaderLap,
                numCars = running.size,
                car = car,
            )
        }
    }
}

@HiltViewModel
class DriveViewModel @Inject constructor(
    repository: TelemetryRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    val hot: HotTelemetry = repository.hot

    val uiState: StateFlow<DriveUiState> = combine(
        repository.status.map { it.isReceiving }.distinctUntilChanged(),
        settings.settings.map { it.drivePreset }.distinctUntilChanged(),
        repository.session.map { it.info }.distinctUntilChanged(),
        repository.race,
        repository.player,
        DriveUiState::build,
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DriveUiState())

    fun setPreset(preset: DrivePreset) {
        viewModelScope.launch { settings.setDrivePreset(preset) }
    }
}
