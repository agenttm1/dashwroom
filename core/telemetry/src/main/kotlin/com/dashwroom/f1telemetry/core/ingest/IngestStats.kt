package com.dashwroom.f1telemetry.core.ingest

import com.dashwroom.f1telemetry.core.parser.HeaderLayout
import com.dashwroom.f1telemetry.core.parser.ParseResult
import com.dashwroom.f1telemetry.core.protocol.PacketId
import com.dashwroom.f1telemetry.core.protocol.PacketSizes
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicLongArray

/**
 * Counters written by the ingest thread (single writer) and read by the status ticker.
 * Plain volatile increments are safe because there is exactly one writer.
 *
 * Packet-loss estimate: Lap Data, Car Telemetry and Car Status are always sent together on the
 * same game frame ("they will all get sent together and will never be separated across frames").
 * For every frame where at least one of the three arrived, the others are expected too; the
 * missing fraction estimates per-packet UDP loss independently of the game's frame rate.
 */
class IngestStats {
    private val received = AtomicLongArray(ID_SLOTS)
    private val sizeMismatch = AtomicLongArray(ID_SLOTS)

    @Volatile var totalDatagrams = 0L; private set
    @Volatile var totalBytes = 0L; private set
    @Volatile var tooShort = 0L; private set
    @Volatile var unknownFormat = 0L; private set
    @Volatile var lastUnknownFormat = -1; private set
    @Volatile var coalesced = 0L; private set
    @Volatile var outOfOrder = 0L; private set
    @Volatile var framesExpected = 0L; private set
    @Volatile var framesMissing = 0L; private set

    /** 0 = never received anything. */
    @Volatile var lastReceivedNanos = 0L; private set
    @Volatile var packetFormat = 0; private set
    @Volatile var gameYear = 0; private set
    @Volatile var gameMajorVersion = 0; private set
    @Volatile var gameMinorVersion = 0; private set
    @Volatile var sessionUid = 0L; private set
    @Volatile var sender: String? = null

    // Loss-tracking state, ingest thread only.
    private var lossSessionUid = 0L
    private var lossFrame = -1L
    private var lossMask = 0

    fun received(packetId: Int): Long = received.get(slot(packetId))

    fun sizeMismatches(packetId: Int): Long = sizeMismatch.get(slot(packetId))

    fun totalSizeMismatches(): Long {
        var sum = 0L
        for (i in 0 until ID_SLOTS) sum += sizeMismatch.get(i)
        return sum
    }

    /** Called for every datagram, before coalescing. Reads header bytes directly; no allocation. */
    fun onReceived(buf: ByteBuffer, length: Int, nanos: Long) {
        totalBytes += length
        if (length < PacketSizes.HEADER) {
            tooShort++
            finish(nanos)
            return
        }
        val id = buf.get(HeaderLayout.PACKET_ID).toInt() and 0xFF
        val s = slot(id)
        received.lazySet(s, received.get(s) + 1)
        val format = buf.getShort(HeaderLayout.PACKET_FORMAT).toInt() and 0xFFFF
        if (format != packetFormat) packetFormat = format
        val year = buf.get(HeaderLayout.GAME_YEAR).toInt() and 0xFF
        if (year != gameYear) gameYear = year
        val major = buf.get(HeaderLayout.GAME_MAJOR_VERSION).toInt() and 0xFF
        if (major != gameMajorVersion) gameMajorVersion = major
        val minor = buf.get(HeaderLayout.GAME_MINOR_VERSION).toInt() and 0xFF
        if (minor != gameMinorVersion) gameMinorVersion = minor
        val uid = buf.getLong(HeaderLayout.SESSION_UID)
        if (uid != sessionUid) sessionUid = uid
        if (id == PacketId.LAP_DATA || id == PacketId.CAR_TELEMETRY || id == PacketId.CAR_STATUS) {
            trackLoss(id, uid, buf.getInt(HeaderLayout.OVERALL_FRAME_IDENTIFIER).toLong() and 0xFFFF_FFFFL)
        }
        finish(nanos)
    }

    fun onParseResult(packetId: Int, result: ParseResult, packetFormatValue: Int) {
        when (result) {
            ParseResult.SIZE_MISMATCH -> {
                val s = slot(packetId)
                sizeMismatch.lazySet(s, sizeMismatch.get(s) + 1)
            }
            ParseResult.UNKNOWN_FORMAT -> {
                unknownFormat++
                lastUnknownFormat = packetFormatValue
            }
            else -> Unit
        }
    }

    fun onCoalesced() {
        coalesced++
    }

    /** Only call while no source is running. */
    fun reset() {
        for (i in 0 until ID_SLOTS) {
            received.set(i, 0)
            sizeMismatch.set(i, 0)
        }
        totalDatagrams = 0; totalBytes = 0; tooShort = 0; unknownFormat = 0; lastUnknownFormat = -1
        coalesced = 0; outOfOrder = 0; framesExpected = 0; framesMissing = 0; lastReceivedNanos = 0
        packetFormat = 0; gameYear = 0; gameMajorVersion = 0; gameMinorVersion = 0; sessionUid = 0
        sender = null
        lossSessionUid = 0; lossFrame = -1; lossMask = 0
    }

    private fun finish(nanos: Long) {
        totalDatagrams++
        lastReceivedNanos = nanos // volatile write last: publishes the counters above to readers
    }

    private fun trackLoss(id: Int, uid: Long, frame: Long) {
        val bit = when (id) {
            PacketId.LAP_DATA -> 1
            PacketId.CAR_TELEMETRY -> 2
            else -> 4
        }
        if (uid != lossSessionUid) {
            lossSessionUid = uid
            lossFrame = -1
            lossMask = 0
        }
        when {
            frame == lossFrame -> lossMask = lossMask or bit
            frame > lossFrame -> {
                if (lossFrame >= 0) {
                    framesExpected += FRAME_SET_SIZE
                    framesMissing += FRAME_SET_SIZE - Integer.bitCount(lossMask)
                }
                lossFrame = frame
                lossMask = bit
            }
            else -> outOfOrder++
        }
    }

    private fun slot(packetId: Int): Int = if (packetId in 0 until PacketId.COUNT) packetId else UNKNOWN_SLOT

    companion object {
        /** Slot [PacketId.COUNT] collects ids outside the known range. */
        const val UNKNOWN_SLOT = PacketId.COUNT
        const val ID_SLOTS = PacketId.COUNT + 1
        private const val FRAME_SET_SIZE = 3
    }
}
