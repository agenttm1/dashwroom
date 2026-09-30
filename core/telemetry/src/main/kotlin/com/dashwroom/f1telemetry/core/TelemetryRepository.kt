package com.dashwroom.f1telemetry.core

import com.dashwroom.f1telemetry.core.ingest.DatagramTap
import com.dashwroom.f1telemetry.core.ingest.IngestStats
import com.dashwroom.f1telemetry.core.ingest.TelemetryPipeline
import com.dashwroom.f1telemetry.core.model.ConnectionState
import com.dashwroom.f1telemetry.core.model.GameInfo
import com.dashwroom.f1telemetry.core.model.PacketTypeStats
import com.dashwroom.f1telemetry.core.model.SessionState
import com.dashwroom.f1telemetry.core.model.TelemetryStatus
import com.dashwroom.f1telemetry.core.protocol.PacketFormat
import com.dashwroom.f1telemetry.core.protocol.PacketId
import com.dashwroom.f1telemetry.core.source.PacketSource
import com.dashwroom.f1telemetry.core.state.HotTelemetry
import com.dashwroom.f1telemetry.core.state.TelemetryStore
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Owns the ingest pipeline and exposes three views of the telemetry:
 *
 * - [hot] — latest per-frame values, read directly by draw code at display rate;
 * - [session] — immutable, slow-changing [SessionState];
 * - [status] — connection + diagnostics, refreshed every [STATUS_INTERVAL_MS].
 *
 * Exactly one [PacketSource] runs at a time, on [ingestDispatcher] (a single dedicated thread),
 * which is also the only thread that ever touches the pipeline.
 */
class TelemetryRepository(
    private val scope: CoroutineScope,
    private val ingestDispatcher: CoroutineDispatcher,
    private val nanoClock: () -> Long = System::nanoTime,
) {
    val hot = HotTelemetry()
    private val stats = IngestStats()
    private val store = TelemetryStore(hot)
    private val pipeline = TelemetryPipeline(stats, store)

    val session: StateFlow<SessionState> = store.session

    private val _status = MutableStateFlow(TelemetryStatus())
    val status: StateFlow<TelemetryStatus> = _status.asStateFlow()

    private val switchLock = Mutex()
    private var sourceJob: Job? = null

    @Volatile private var currentSource: PacketSource? = null

    @Volatile private var sourceError: String? = null

    private val rateWindow = RateWindow(windowTicks = (1_000 / STATUS_INTERVAL_MS).toInt())
    private val lossWindow = RateWindow(windowTicks = (5_000 / STATUS_INTERVAL_MS).toInt())

    init {
        scope.launch {
            while (isActive) {
                publishStatus()
                delay(STATUS_INTERVAL_MS)
            }
        }
    }

    /** Stops the running source (if any), clears all state, and starts [source] (null = idle). */
    fun setSource(source: PacketSource?) {
        scope.launch {
            switchLock.withLock {
                sourceJob?.let {
                    it.cancel()
                    it.join()
                }
                currentSource = source
                sourceError = null
                withContext(ingestDispatcher) { pipeline.reset() }
                rateWindow.clear()
                lossWindow.clear()
                sourceJob = if (source == null) null else scope.launch(ingestDispatcher) { runWithRetry(source) }
            }
        }
    }

    /** Installs or removes the raw-datagram tap (recorder). Thread-safe. */
    fun setTap(tap: DatagramTap?) {
        pipeline.tap = tap
    }

    private suspend fun runWithRetry(source: PacketSource) {
        while (scope.isActive) {
            try {
                source.run(pipeline)
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                sourceError = e.message ?: e.javaClass.simpleName
                delay(RETRY_DELAY_MS)
            }
        }
    }

    private fun publishStatus() {
        val now = nanoClock()
        val last = stats.lastReceivedNanos
        val ageMs = if (last == 0L) null else (now - last) / 1_000_000
        rateWindow.push(now, stats.totalDatagrams, stats.totalBytes, stats.received(PacketId.CAR_TELEMETRY), perTypeCounts())
        lossWindow.push(now, stats.framesExpected, stats.framesMissing, 0, null)

        val seconds = rateWindow.spanSeconds()
        val perType = ArrayList<PacketTypeStats>(PacketId.COUNT + 1)
        for (id in 0..PacketId.COUNT) {
            val count = if (id == PacketId.COUNT) stats.received(-1) else stats.received(id)
            if (count == 0L) continue
            perType += PacketTypeStats(
                packetId = id,
                name = if (id == PacketId.COUNT) "Unknown id" else PacketId.name(id),
                count = count,
                perSecond = rateWindow.perTypeRate(id, seconds),
                sizeMismatches = stats.sizeMismatches(if (id == PacketId.COUNT) -1 else id),
            )
        }
        val expected = lossWindow.deltaA()
        val missing = lossWindow.deltaB()
        val format = stats.packetFormat
        val source = currentSource
        _status.value = TelemetryStatus(
            connection = ConnectionState.fromAge(ageMs),
            source = source?.kind,
            sourceDescription = source?.description.orEmpty(),
            sourceError = sourceError,
            packetsPerSecond = rateWindow.rateA(seconds),
            telemetryHz = rateWindow.rateC(seconds),
            bytesPerSecond = rateWindow.rateB(seconds),
            lossPercent = if (expected >= MIN_FRAMES_FOR_LOSS) missing * 100f / expected else null,
            game = if (format == 0) null else GameInfo(
                packetFormat = format,
                format = PacketFormat.fromWire(format),
                gameYear = stats.gameYear,
                majorVersion = stats.gameMajorVersion,
                minorVersion = stats.gameMinorVersion,
            ),
            sessionUid = stats.sessionUid.takeIf { it != 0L },
            sender = stats.sender,
            totalPackets = stats.totalDatagrams,
            msSinceLastPacket = ageMs,
            perType = perType.toImmutableList(),
            unknownFormatPackets = stats.unknownFormat,
            lastUnknownFormat = stats.lastUnknownFormat.takeIf { it >= 0 },
            sizeMismatches = stats.totalSizeMismatches(),
            coalescedPackets = stats.coalesced,
            outOfOrderPackets = stats.outOfOrder,
            tooShortPackets = stats.tooShort,
        )
    }

    private fun perTypeCounts(): LongArray = LongArray(PacketId.COUNT + 1) { id ->
        if (id == PacketId.COUNT) stats.received(-1) else stats.received(id)
    }

    companion object {
        const val STATUS_INTERVAL_MS = 250L
        private const val RETRY_DELAY_MS = 2_000L
        private const val MIN_FRAMES_FOR_LOSS = 30L
    }
}

/** Ring of cumulative counters sampled by the status ticker; rates come from first/last diff. */
private class RateWindow(private val windowTicks: Int) {
    private val size = windowTicks + 1
    private val t = LongArray(size)
    private val a = LongArray(size)
    private val b = LongArray(size)
    private val c = LongArray(size)
    private val perType = arrayOfNulls<LongArray>(size)
    private var head = -1
    private var filled = 0

    fun clear() {
        head = -1
        filled = 0
    }

    fun push(now: Long, va: Long, vb: Long, vc: Long, types: LongArray?) {
        head = (head + 1) % size
        t[head] = now; a[head] = va; b[head] = vb; c[head] = vc; perType[head] = types
        if (filled < size) filled++
    }

    private val oldest: Int get() = if (filled < size) 0 else (head + 1) % size

    fun spanSeconds(): Float = if (filled < 2) 0f else (t[head] - t[oldest]) / 1e9f

    fun rateA(s: Float) = if (s <= 0f) 0f else (a[head] - a[oldest]) / s
    fun rateB(s: Float) = if (s <= 0f) 0f else (b[head] - b[oldest]) / s
    fun rateC(s: Float) = if (s <= 0f) 0f else (c[head] - c[oldest]) / s
    fun deltaA(): Long = if (filled < 2) 0 else a[head] - a[oldest]
    fun deltaB(): Long = if (filled < 2) 0 else b[head] - b[oldest]

    fun perTypeRate(id: Int, s: Float): Float {
        if (s <= 0f) return 0f
        val newest = perType[head] ?: return 0f
        val first = perType[oldest] ?: return 0f
        return (newest[id] - first[id]) / s
    }
}
