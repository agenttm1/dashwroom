package com.dashwroom.f1telemetry.core.source

import com.dashwroom.f1telemetry.core.PacketFixtures
import com.dashwroom.f1telemetry.core.TelemetryRepository
import com.dashwroom.f1telemetry.core.encodeAny
import com.dashwroom.f1telemetry.core.model.ConnectionState
import com.dashwroom.f1telemetry.core.protocol.PacketFormat
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.Executors

/** Real sockets on localhost: bytes in → parsed session state and diagnostics out. */
class UdpIntegrationTest {
    @Test
    fun `packets sent over UDP reach the session model and diagnostics`(): Unit = runBlocking {
        val port = DatagramSocket(0).use { it.localPort }
        val executor = Executors.newSingleThreadExecutor()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val repo = TelemetryRepository(scope, executor.asCoroutineDispatcher())
            repo.setSource(DatagramChannelSource(port))

            val fixtures = PacketFixtures()
            val format = PacketFormat.F1_25_SEASON_2026
            val session = fixtures.session(format).apply { header.sessionUid = 42; trackId = 7; sessionType = 15 }
            val telemetry = fixtures.telemetry(format).apply { header.sessionUid = 42; header.playerCarIndex = 1; cars[1].speedKph = 287 }
            val datagrams = listOf(encodeAny(session), encodeAny(telemetry)).map { it.array() }

            DatagramSocket().use { socket ->
                withTimeout(10_000) {
                    while (repo.session.value.info == null || repo.hot.speedKph != 287) {
                        for (d in datagrams) socket.send(DatagramPacket(d, d.size, InetAddress.getLoopbackAddress(), port))
                        kotlinx.coroutines.delay(50)
                    }
                    repo.status.first { it.connection == ConnectionState.CONNECTED && it.game != null }
                }
            }
            val info = repo.session.value.info!!
            assertThat(info.trackName).isEqualTo("Silverstone")
            assertThat(info.sessionTypeName).isEqualTo("Race")
            val status = repo.status.value
            assertThat(status.game!!.format).isEqualTo(format)
            assertThat(status.sessionUid).isEqualTo(42L)
            assertThat(status.sender).isEqualTo("127.0.0.1")
            assertThat(status.perType.map { it.packetId }).containsAtLeast(1, 6)
        } finally {
            scope.cancel()
            executor.shutdownNow()
        }
    }
}
