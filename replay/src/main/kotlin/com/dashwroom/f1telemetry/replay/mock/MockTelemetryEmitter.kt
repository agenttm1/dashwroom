package com.dashwroom.f1telemetry.replay.mock

import com.dashwroom.f1telemetry.core.encode.PacketEncoder
import com.dashwroom.f1telemetry.core.encode.StructWriter
import com.dashwroom.f1telemetry.core.ingest.DatagramSink
import com.dashwroom.f1telemetry.core.model.SourceKind
import com.dashwroom.f1telemetry.core.protocol.PacketFormat
import com.dashwroom.f1telemetry.core.protocol.PacketId
import com.dashwroom.f1telemetry.core.protocol.PacketSizes
import com.dashwroom.f1telemetry.core.source.PacketSource
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.concurrent.locks.LockSupport

/**
 * Generates a synthetic but realistic race and emits spec-exact datagrams, so the whole app can be
 * developed and demoed without the game. Follows the game's cadence: Motion, Lap Data, Car
 * Telemetry and Car Status every frame at [rateHz]; Session twice a second; Participants every
 * 5 s; Events as they happen. When a race ends, a new session (new UID) starts shortly after.
 *
 * The per-frame path allocates nothing, so running the mock doesn't pollute allocation profiling.
 */
class MockTelemetryEmitter(
    private val format: PacketFormat = PacketFormat.F1_25,
    private val rateHz: Int = 60,
    private val totalLaps: Int = 20,
    private val seed: Long = 42L,
    /** Time multiplier for the simulation clock (1 = real time). */
    private val timeScale: Float = 1f,
) : PacketSource {
    override val kind = SourceKind.MOCK
    override val description = "Mock race · ${format.displayName} · ${rateHz}Hz"

    private val track = MockTrack()
    private var race = MockRace(format, totalLaps, seed, track)
    private var sessionUid = seed * 7_919 + 1
    private var builder = MockPacketBuilder(race, sessionUid)
    private val writer = StructWriter(PacketSizes.MAX_DATAGRAM)
    private var nextSessionAt = 0f
    private var nextParticipantsAt = 0f
    private var sessionCount = 0

    val currentRace: MockRace get() = race

    override suspend fun run(sink: DatagramSink) {
        sink.onSender("mock")
        val frameNanos = 1_000_000_000L / rateHz
        var deadline = System.nanoTime()
        while (true) {
            currentCoroutineContext().ensureActive()
            emitFrame(sink, System.nanoTime())
            deadline += frameNanos
            val now = System.nanoTime()
            if (deadline - now < -250_000_000L) deadline = now // fell far behind: don't burst to catch up
            while (true) {
                val wait = deadline - System.nanoTime()
                if (wait <= 0) break
                LockSupport.parkNanos(wait)
            }
        }
    }

    /** Advances one frame and delivers its packets. Synchronous; used by tests and benchmarks. */
    fun emitFrame(sink: DatagramSink, nowNanos: Long) {
        val finishedAt = race.finishedAt
        if (finishedAt >= 0f && race.sessionTime - finishedAt > RESTART_AFTER_S) startNewSession()

        race.step(timeScale / rateHz)
        builder.frame++
        val t = race.sessionTime

        if (t >= nextSessionAt) {
            nextSessionAt += SESSION_INTERVAL_S
            deliver(sink, nowNanos) { PacketEncoder.session(it, builder.buildSession()) }
        }
        if (t >= nextParticipantsAt) {
            nextParticipantsAt += PARTICIPANTS_INTERVAL_S
            deliver(sink, nowNanos) { PacketEncoder.participants(it, builder.buildParticipants()) }
        }
        deliver(sink, nowNanos) { PacketEncoder.lapData(it, builder.buildLapData()) }
        deliver(sink, nowNanos) { PacketEncoder.carStatus(it, builder.buildStatus()) }
        deliver(sink, nowNanos) { PacketEncoder.carTelemetry(it, builder.buildTelemetry()) }
        deliver(sink, nowNanos) { PacketEncoder.motion(it, builder.buildMotion()) }
        while (true) {
            val event = race.events.poll() ?: break
            builder.header(event.header, PacketId.EVENT)
            deliver(sink, nowNanos) { PacketEncoder.event(it, event) }
        }
        sink.onBurstEnd()
    }

    private inline fun deliver(sink: DatagramSink, nowNanos: Long, encode: (StructWriter) -> Unit) {
        writer.reset()
        encode(writer)
        val length = writer.position
        sink.onDatagram(writer.buffer, length, nowNanos)
    }

    private fun startNewSession() {
        sessionCount++
        race = MockRace(format, totalLaps, seed + sessionCount, track)
        sessionUid += 1
        builder = MockPacketBuilder(race, sessionUid)
        nextSessionAt = 0f
        nextParticipantsAt = 0f
    }

    private companion object {
        const val RESTART_AFTER_S = 8f
        const val SESSION_INTERVAL_S = 0.5f
        const val PARTICIPANTS_INTERVAL_S = 5f
    }
}
