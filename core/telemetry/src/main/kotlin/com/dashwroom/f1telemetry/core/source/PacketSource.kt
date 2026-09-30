package com.dashwroom.f1telemetry.core.source

import com.dashwroom.f1telemetry.core.ingest.DatagramSink
import com.dashwroom.f1telemetry.core.model.SourceKind

/**
 * Produces datagrams: the live UDP socket, the mock emitter, or a replayed recording.
 *
 * [run] executes on the dedicated ingest thread and loops until its coroutine is cancelled.
 * Blocking waits must be short (≤ 250 ms) so cancellation is prompt. Throwing ends the run; the
 * repository reports the message and retries.
 */
interface PacketSource {
    val kind: SourceKind
    val description: String

    suspend fun run(sink: DatagramSink)
}
