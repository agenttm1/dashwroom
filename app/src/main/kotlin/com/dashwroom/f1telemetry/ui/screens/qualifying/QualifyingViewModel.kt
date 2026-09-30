package com.dashwroom.f1telemetry.ui.screens.qualifying

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dashwroom.f1telemetry.core.TelemetryRepository
import com.dashwroom.f1telemetry.core.model.DriverState
import com.dashwroom.f1telemetry.core.model.HistoryState
import com.dashwroom.f1telemetry.core.model.RaceState
import com.dashwroom.f1telemetry.core.model.SessionInfo
import com.dashwroom.f1telemetry.core.state.HotTelemetry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Knock-out rules for the current qualifying part. */
@Immutable
data class Knockout(
    /** Label, e.g. "Q1". */
    val part: String,
    /** Cars from this position down are eliminated; 0 = no elimination in this part. */
    val firstEliminated: Int,
    val eliminatedCount: Int,
) {
    companion object {
        /**
         * Standard format: each of Q1 and Q2 drops five cars on a 20-car grid, six on the 2026
         * 22-car grid; Q3 (and one-shot / short qualifying) drops nobody.
         */
        fun of(info: SessionInfo?, fieldSize: Int): Knockout? {
            if (info == null || !info.isQualifying) return null
            val k = if (fieldSize >= 22) 6 else 5
            return when (info.sessionType) {
                5, 10 -> Knockout(if (info.sessionType == 5) "Q1" else "SQ1", fieldSize - k + 1, k)
                6, 11 -> Knockout(if (info.sessionType == 6) "Q2" else "SQ2", fieldSize - 2 * k + 1, k)
                7, 12 -> Knockout(if (info.sessionType == 7) "Q3" else "SQ3", 0, 0)
                else -> Knockout(info.sessionTypeName, 0, 0)
            }
        }
    }
}

@Immutable
data class QualifyingUiState(
    val receiving: Boolean = false,
    val info: SessionInfo? = null,
    val race: RaceState = RaceState(),
    /** Ranked by best lap; drivers without a time follow in running order. */
    val leaderboard: ImmutableList<DriverState> = persistentListOf(),
    val history: HistoryState = HistoryState(),
    val knockout: Knockout? = null,
    val selected: Int = -1,
) {
    val hasData: Boolean get() = receiving && leaderboard.isNotEmpty()
    val poleMs: Long get() = leaderboard.firstOrNull()?.bestLapTimeMs ?: 0L

    companion object {
        fun rank(drivers: List<DriverState>): List<DriverState> {
            val (timed, untimed) = drivers.filter { it.resultStatus != com.dashwroom.f1telemetry.core.model.ResultStatus.INVALID }
                .partition { it.bestLapTimeMs > 0 }
            return timed.sortedBy { it.bestLapTimeMs } + untimed.sortedBy { if (it.position > 0) it.position else 99 }
        }
    }
}

@HiltViewModel
class QualifyingViewModel @Inject constructor(
    repository: TelemetryRepository,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    val hot: HotTelemetry = repository.hot

    val uiState: StateFlow<QualifyingUiState> = combine(
        repository.status.map { it.isReceiving }.distinctUntilChanged(),
        repository.session.map { it.info }.distinctUntilChanged(),
        repository.race,
        repository.history,
        savedState.getStateFlow(KEY_SELECTED, -1),
    ) { receiving, info, race, history, selected ->
        val ranked = QualifyingUiState.rank(race.drivers)
        QualifyingUiState(
            receiving = receiving,
            info = info,
            race = race,
            leaderboard = ranked.toImmutableList(),
            history = history,
            knockout = Knockout.of(info, ranked.size),
            selected = selected,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), QualifyingUiState())

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
