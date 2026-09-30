package com.dashwroom.f1telemetry.replay.record

/**
 * `.bin` capture format (little-endian):
 *
 * ```
 * File header, 16 bytes:
 *   0  4  magic "DWR1"
 *   4  2  uint16 version (1)
 *   6  2  uint16 reserved (0)
 *   8  8  int64  wall-clock start, epoch milliseconds
 * Then repeated records:
 *   0  8  int64  receive time, nanoseconds since the recording started
 *   8  2  uint16 datagram length N
 *  10  N  raw datagram bytes, exactly as received from the game
 * ```
 * Raw datagrams are stored before any coalescing, so a replay is byte-identical to the session.
 */
object RecordingFormat {
    val MAGIC = byteArrayOf('D'.code.toByte(), 'W'.code.toByte(), 'R'.code.toByte(), '1'.code.toByte())
    const val VERSION = 1
    const val FILE_HEADER_SIZE = 16
    const val RECORD_HEADER_SIZE = 10
    const val FILE_EXTENSION = "bin"
}
