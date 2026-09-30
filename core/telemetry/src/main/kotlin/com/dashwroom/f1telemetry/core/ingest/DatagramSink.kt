package com.dashwroom.f1telemetry.core.ingest

import java.nio.ByteBuffer

/**
 * Where packet sources deliver raw datagrams. All calls come from the single ingest thread.
 *
 * [buffer] is little-endian, array-backed, with the datagram at index 0..length-1. It is only
 * valid for the duration of the call — the source reuses it for the next receive.
 */
interface DatagramSink {
    fun onDatagram(buffer: ByteBuffer, length: Int, receivedAtNanos: Long)

    /** The source has drained everything currently available; flush coalesced packets. */
    fun onBurstEnd()

    /** Reported once per sender change, so diagnostics can show where data comes from. */
    fun onSender(address: String) {}
}

/** Observes every raw datagram before coalescing (the recorder hooks in here). */
fun interface DatagramTap {
    fun onDatagram(buffer: ByteBuffer, length: Int, receivedAtNanos: Long)
}
