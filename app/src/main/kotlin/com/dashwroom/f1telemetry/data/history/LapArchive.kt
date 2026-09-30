package com.dashwroom.f1telemetry.data.history

import com.dashwroom.f1telemetry.core.TelemetryRepository
import com.dashwroom.f1telemetry.core.model.LapTrace
import com.dashwroom.f1telemetry.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Archives every lap the player completes (with its telemetry traces) into Room, so Lap Analysis
 * can compare laps across sessions. Runs for the process lifetime once [start]ed.
 */
@Singleton
class LapArchive @Inject constructor(
    private val repository: TelemetryRepository,
    private val dao: HistoryDao,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val started = AtomicBoolean(false)

    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch(Dispatchers.IO) {
            repository.completedLaps.collect { trace -> runCatching { save(trace) } }
        }
    }

    suspend fun save(trace: LapTrace) = withContext(Dispatchers.IO) {
        if (trace.sessionUid == 0L || trace.lapTimeMs <= 0) return@withContext
        val session = repository.session.value
        val info = session.info
        val now = System.currentTimeMillis()
        dao.saveLap(
            SessionEntity(
                uid = trace.sessionUid,
                startedAtMillis = now,
                updatedAtMillis = now,
                trackId = info?.trackId ?: -1,
                trackName = info?.trackName ?: "Unknown track",
                sessionType = info?.sessionType ?: 0,
                sessionTypeName = info?.sessionTypeName ?: "Session",
                packetFormat = session.format?.wireValue ?: 0,
            ),
            LapEntity(
                sessionUid = trace.sessionUid,
                lapNumber = trace.lapNumber,
                lapTimeMs = trace.lapTimeMs,
                s1Ms = trace.sectorsMs.s1Ms,
                s2Ms = trace.sectorsMs.s2Ms,
                s3Ms = trace.sectorsMs.s3Ms,
                valid = trace.valid,
                tyreVisual = trace.tyreVisual,
                trackLengthM = trace.trackLengthM,
                traces = LapTraceCodec.encode(trace),
            ),
        )
        dao.trimSessions(MAX_SESSIONS)
    }

    fun sessions(): Flow<List<SessionSummaryRow>> = dao.sessions()

    fun laps(sessionUid: Long): Flow<List<LapSummaryRow>> = dao.laps(sessionUid)

    suspend fun trace(sessionUid: Long, lap: Int): LapTrace? = withContext(Dispatchers.IO) {
        dao.lap(sessionUid, lap)?.let { runCatching { LapTraceCodec.decode(it) }.getOrNull() }
    }

    suspend fun delete(sessionUid: Long) = withContext(Dispatchers.IO) { dao.deleteSession(sessionUid) }

    companion object {
        const val MAX_SESSIONS = 50
    }
}
