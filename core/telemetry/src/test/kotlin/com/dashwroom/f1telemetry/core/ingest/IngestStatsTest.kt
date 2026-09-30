package com.dashwroom.f1telemetry.core.ingest

import com.dashwroom.f1telemetry.core.blankPacket
import com.dashwroom.f1telemetry.core.model.ConnectionState
import com.dashwroom.f1telemetry.core.parser.HeaderLayout
import com.dashwroom.f1telemetry.core.protocol.PacketFormat
import com.dashwroom.f1telemetry.core.protocol.PacketId
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.nio.ByteBuffer

class IngestStatsTest {
    private fun packet(id: Int, frame: Long, uid: Long = 5L): ByteBuffer =
        blankPacket(PacketFormat.F1_25, id).apply {
            putLong(HeaderLayout.SESSION_UID, uid)
            putInt(HeaderLayout.OVERALL_FRAME_IDENTIFIER, frame.toInt())
        }

    @Test
    fun `counts every datagram per packet id`() {
        val stats = IngestStats()
        repeat(3) { stats.onReceived(packet(PacketId.CAR_TELEMETRY, it.toLong()), 1352, 10) }
        stats.onReceived(packet(PacketId.SESSION, 0), 753, 20)
        assertThat(stats.received(PacketId.CAR_TELEMETRY)).isEqualTo(3)
        assertThat(stats.received(PacketId.SESSION)).isEqualTo(1)
        assertThat(stats.totalDatagrams).isEqualTo(4)
        assertThat(stats.lastReceivedNanos).isEqualTo(20)
        assertThat(stats.packetFormat).isEqualTo(2025)
    }

    @Test
    fun `loss estimate counts missing members of each frame's lap-telemetry-status set`() {
        val stats = IngestStats()
        for (frame in 1L..101L) {
            // Frames 1..100 are finalised when the next frame starts; drop telemetry on every 10th.
            stats.onReceived(packet(PacketId.LAP_DATA, frame), 1285, frame)
            if (frame % 10 != 0L) stats.onReceived(packet(PacketId.CAR_TELEMETRY, frame), 1352, frame)
            stats.onReceived(packet(PacketId.CAR_STATUS, frame), 1239, frame)
        }
        assertThat(stats.framesExpected).isEqualTo(300)
        assertThat(stats.framesMissing).isEqualTo(10)
    }

    @Test
    fun `game frame rate above the send rate does not look like loss`() {
        val stats = IngestStats()
        // 120 fps game, 60 Hz send rate: frame ids advance by 2 — no packets are actually missing.
        for (frame in 0L..200L step 2) {
            for (id in listOf(PacketId.LAP_DATA, PacketId.CAR_TELEMETRY, PacketId.CAR_STATUS)) {
                stats.onReceived(packet(id, frame), 1285, frame)
            }
        }
        assertThat(stats.framesMissing).isEqualTo(0)
        assertThat(stats.framesExpected).isGreaterThan(0)
    }

    @Test
    fun `connection state thresholds`() {
        assertThat(ConnectionState.fromAge(null)).isEqualTo(ConnectionState.SEARCHING)
        assertThat(ConnectionState.fromAge(0)).isEqualTo(ConnectionState.CONNECTED)
        assertThat(ConnectionState.fromAge(1_000)).isEqualTo(ConnectionState.CONNECTED)
        assertThat(ConnectionState.fromAge(1_001)).isEqualTo(ConnectionState.STALE)
        assertThat(ConnectionState.fromAge(3_000)).isEqualTo(ConnectionState.STALE)
        assertThat(ConnectionState.fromAge(3_001)).isEqualTo(ConnectionState.SEARCHING)
    }
}
