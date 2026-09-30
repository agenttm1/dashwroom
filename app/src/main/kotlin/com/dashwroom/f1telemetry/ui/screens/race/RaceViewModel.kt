package com.dashwroom.f1telemetry.ui.screens.race

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dashwroom.f1telemetry.core.TelemetryRepository
import com.dashwroom.f1telemetry.core.model.HistoryState
import com.dashwroom.f1telemetry.core.model.RaceEvent
import com.dashwroom.f1telemetry.core.model.RaceState
import com.dashwroom.f1telemetry.core.model.SessionInfo
import com.dashwroom.f1telemetry.core.state.HotTelemetry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@Immutable
data class RaceUiState(
    val receiving: Boolean = false,
    val info: SessionInfo? = null,
    val race: RaceState = RaceState(),
    val history: HistoryState = HistoryState(),
    val events: ImmutableList<RaceEvent> = persistentListOf(),
    /** Vehicle index shown in the detail pane / sheet; -1 = none. */
    val selected: Int = -1,
    val pitAdvice: PitAdvice? = null,
) {
    val hasData: Boolean get() = receiving && race.drivers.isNotEmpty()
}

@HiltViewModel
class RaceViewModel @Inject constructor(
    repository: TelemetryRepository,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    val hot: HotTelemetry = repository.hot

    private val selected = savedState.getStateFlow(KEY_SELECTED, -1)

    val uiState: StateFlow<RaceUiState> = combine(
        repository.status.map { it.isReceiving }.distinctUntilChanged(),
        repository.session.map { it.info }.distinctUntilChanged(),
        repository.race,
        repository.history,
        repository.events,
    ) { receiving, info, race, history, events ->
        RaceUiState(receiving, info, race, history, events, pitAdvice = PitStrategy.compute(race, info))
    }.combine(selected) { state, sel -> state.copy(selected = sel) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RaceUiState())

    fun select(vehicleIndex: Int) {
        savedState[KEY_SELECTED] = if (savedState.get<Int>(KEY_SELECTED) == vehicleIndex) -1 else vehicleIndex
    }

    fun clearSelection() {
        savedState[KEY_SELECTED] = -1
    }

    private companion object {
        const val KEY_SELECTED = "selected"
    }
}
