package com.dashwroom.f1telemetry.ui.screens.car

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dashwroom.f1telemetry.core.TelemetryRepository
import com.dashwroom.f1telemetry.core.model.PlayerCarState
import com.dashwroom.f1telemetry.core.model.SessionInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@Immutable
data class CarUiState(
    val receiving: Boolean = false,
    val info: SessionInfo? = null,
    val car: PlayerCarState = PlayerCarState(),
) {
    val hasData: Boolean get() = receiving && car.hasTelemetry
}

@HiltViewModel
class CarViewModel @Inject constructor(repository: TelemetryRepository) : ViewModel() {
    val uiState: StateFlow<CarUiState> = combine(
        repository.status.map { it.isReceiving }.distinctUntilChanged(),
        repository.session.map { it.info }.distinctUntilChanged(),
        repository.player,
    ) { receiving, info, car -> CarUiState(receiving, info, car) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CarUiState())
}
