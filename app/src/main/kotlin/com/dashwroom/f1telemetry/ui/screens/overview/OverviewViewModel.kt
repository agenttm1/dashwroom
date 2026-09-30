package com.dashwroom.f1telemetry.ui.screens.overview

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dashwroom.f1telemetry.core.TelemetryRepository
import com.dashwroom.f1telemetry.core.model.DriverState
import com.dashwroom.f1telemetry.core.model.PlayerCarState
import com.dashwroom.f1telemetry.core.model.RaceState
import com.dashwroom.f1telemetry.core.model.SessionInfo
import com.dashwroom.f1telemetry.core.model.SessionState
import com.dashwroom.f1telemetry.core.model.TrackOutline
import com.dashwroom.f1telemetry.core.state.HotTelemetry
import com.dashwroom.f1telemetry.ui.components.MapCars
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@Immutable
data class OverviewUiState(
    val receiving: Boolean = false,
    val info: SessionInfo? = null,
    val race: RaceState = RaceState(),
    val player: DriverState? = null,
    val ahead: DriverState? = null,
    val behind: DriverState? = null,
    val car: PlayerCarState = PlayerCarState(),
    val outline: TrackOutline? = null,
    val mapCars: MapCars = MapCars(emptyList(), -1),
) {
    val hasData: Boolean get() = receiving && (info != null || player != null)

    companion object {
        fun build(receiving: Boolean, session: SessionState, race: RaceState, car: PlayerCarState, outline: TrackOutline?): OverviewUiState {
            val player = race.player
            val running = race.drivers.filter { it.isRunning || it.position > 0 }
            val ahead = player?.let { p -> running.firstOrNull { it.position == p.position - 1 } }
            val behind = player?.let { p -> running.firstOrNull { it.position == p.position + 1 } }
            return OverviewUiState(
                receiving = receiving,
                info = session.info,
                race = race,
                player = player,
                ahead = ahead,
                behind = behind,
                car = car,
                outline = outline,
                mapCars = MapCars.from(race, session.playerCarIndex),
            )
        }
    }
}

@HiltViewModel
class OverviewViewModel @Inject constructor(repository: TelemetryRepository) : ViewModel() {
    val hot: HotTelemetry = repository.hot

    val uiState: StateFlow<OverviewUiState> = combine(
        repository.status.map { it.isReceiving }.distinctUntilChanged(),
        repository.session,
        repository.race,
        repository.player,
        repository.trackOutline,
        OverviewUiState::build,
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OverviewUiState())
}
