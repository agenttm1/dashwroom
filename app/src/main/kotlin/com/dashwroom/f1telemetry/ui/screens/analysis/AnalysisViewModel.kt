package com.dashwroom.f1telemetry.ui.screens.analysis

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dashwroom.f1telemetry.core.TelemetryRepository
import com.dashwroom.f1telemetry.core.model.LapTrace
import com.dashwroom.f1telemetry.core.model.SectorTimes
import com.dashwroom.f1telemetry.data.history.LapArchive
import com.dashwroom.f1telemetry.data.history.SessionSummaryRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class SessionOption(val uid: Long, val title: String, val subtitle: String, val live: Boolean)

@Immutable
data class LapSummary(
    val lap: Int,
    val lapTimeMs: Long,
    val sectors: SectorTimes,
    val valid: Boolean,
    val tyreVisual: Int,
    val hasTrace: Boolean,
)

@Immutable
data class AnalysisUiState(
    val sessions: ImmutableList<SessionOption> = persistentListOf(),
    val sessionUid: Long = 0L,
    val laps: ImmutableList<LapSummary> = persistentListOf(),
    val lapA: Int = -1,
    val lapB: Int = -1,
    val traceA: LapTrace? = null,
    val traceB: LapTrace? = null,
) {
    val bestLapMs: Long get() = laps.filter { it.valid && it.lapTimeMs > 0 }.minOfOrNull { it.lapTimeMs } ?: 0L
    fun bestSector(i: Int): Int = laps.map { it.sectors.get(i) }.filter { it > 0 }.minOrNull() ?: 0
}

private data class Selection(val source: Long, val a: Int, val b: Int)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AnalysisViewModel @Inject constructor(
    private val repository: TelemetryRepository,
    private val archive: LapArchive,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    private val selection: Flow<Selection> = combine(
        savedState.getStateFlow(KEY_SOURCE, 0L),
        savedState.getStateFlow(KEY_A, -1),
        savedState.getStateFlow(KEY_B, -1),
    ) { s, a, b -> Selection(s, a, b) }

    private val currentUid = repository.session.map { it.sessionUid }.distinctUntilChanged()

    private val sessions: Flow<ImmutableList<SessionOption>> = combine(archive.sessions(), currentUid, repository.session) { rows, uid, session ->
        val live = if (uid != 0L) {
            val info = session.info
            SessionOption(uid, "Current session", listOfNotNull(info?.trackName, info?.sessionTypeName).joinToString(" · "), live = true)
        } else {
            null
        }
        (listOfNotNull(live) + rows.filter { it.uid != uid }.map(::option)).toImmutableList()
    }

    /** Laps of the chosen session: live history + recorded traces, or the archive. */
    private val laps: Flow<Pair<Long, List<LapSummary>>> = combine(selection.map { it.source }.distinctUntilChanged(), currentUid) { s, uid -> if (s == 0L) uid else s }
        .distinctUntilChanged()
        .flatMapLatest { uid ->
            if (uid == 0L) {
                flow { emit(0L to emptyList()) }
            } else {
                combine(currentUid, repository.laps, repository.history, archive.laps(uid)) { cur, traces, history, archived ->
                    if (uid == cur) {
                        val recorded = traces.associateBy { it.lapNumber }
                        val gameLaps = history.drivers[repository.session.value.playerCarIndex]?.laps.orEmpty()
                        val fromHistory = gameLaps.filter { it.lapTimeMs > 0 }.map {
                            LapSummary(it.lap, it.lapTimeMs, it.sectorsMs, it.valid, it.tyreVisual, hasTrace = it.lap in recorded)
                        }
                        val known = fromHistory.map { it.lap }.toSet()
                        val onlyTraces = traces.filter { it.lapNumber !in known }.map {
                            LapSummary(it.lapNumber, it.lapTimeMs, it.sectorsMs, it.valid, it.tyreVisual, hasTrace = true)
                        }
                        uid to (fromHistory + onlyTraces).sortedBy { it.lap }
                    } else {
                        uid to archived.map { LapSummary(it.lapNumber, it.lapTimeMs, SectorTimes(it.s1Ms, it.s2Ms, it.s3Ms), it.valid, it.tyreVisual, hasTrace = true) }
                    }
                }
            }
        }

    private fun traceFlow(uidFlow: Flow<Long>, lapFlow: Flow<Int>): Flow<LapTrace?> =
        combine(uidFlow, lapFlow, currentUid) { uid, lap, cur -> Triple(uid, lap, cur) }
            .distinctUntilChanged()
            .flatMapLatest { (uid, lap, cur) ->
                if (lap < 0 || uid == 0L) {
                    flow { emit(null) }
                } else if (uid == cur) {
                    repository.laps.map { list -> list.lastOrNull { it.lapNumber == lap } }.distinctUntilChanged()
                } else {
                    flow { emit(archive.trace(uid, lap)) }
                }
            }

    private val resolvedA: Flow<Int> = combine(selection, laps) { sel, (_, list) ->
        if (sel.a >= 0 && list.any { it.lap == sel.a }) sel.a else defaultLap(list)
    }.distinctUntilChanged()

    private val sourceUid: Flow<Long> = laps.map { it.first }.distinctUntilChanged()
    private val lapB: Flow<Int> = selection.map { it.b }.distinctUntilChanged()

    val uiState: StateFlow<AnalysisUiState> = combine(
        sessions,
        laps,
        combine(resolvedA, lapB) { a, b -> a to b },
        traceFlow(sourceUid, resolvedA),
        traceFlow(sourceUid, lapB),
    ) { sessions, (uid, list), (a, b), ta, tb ->
        AnalysisUiState(sessions, uid, list.toImmutableList(), a, b, ta, if (b == a) null else tb)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnalysisUiState())

    fun selectSession(uid: Long) {
        savedState[KEY_SOURCE] = if (uid == repository.session.value.sessionUid) 0L else uid
        savedState[KEY_A] = -1
        savedState[KEY_B] = -1
    }

    fun selectLap(lap: Int) {
        savedState[KEY_A] = lap
        if (savedState.get<Int>(KEY_B) == lap) savedState[KEY_B] = -1
    }

    /** Toggles [lap] as the comparison lap. */
    fun compareLap(lap: Int) {
        savedState[KEY_B] = if (savedState.get<Int>(KEY_B) == lap) -1 else lap
    }

    fun deleteSession(uid: Long) {
        viewModelScope.launch { archive.delete(uid) }
        if (savedState.get<Long>(KEY_SOURCE) == uid) selectSession(0L)
    }

    private fun defaultLap(list: List<LapSummary>): Int =
        list.filter { it.valid && it.hasTrace }.minByOrNull { it.lapTimeMs }?.lap
            ?: list.lastOrNull { it.hasTrace }?.lap
            ?: -1

    private fun option(row: SessionSummaryRow): SessionOption {
        val date = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(row.startedAtMillis))
        val best = row.bestLapMs?.let { " · best " + com.dashwroom.f1telemetry.ui.format.Fmt.lapTime(it) }.orEmpty()
        return SessionOption(row.uid, "${row.trackName} · ${row.sessionTypeName}", "$date · ${row.lapCount} laps$best", live = false)
    }

    private companion object {
        const val KEY_SOURCE = "source"
        const val KEY_A = "lapA"
        const val KEY_B = "lapB"
    }
}
