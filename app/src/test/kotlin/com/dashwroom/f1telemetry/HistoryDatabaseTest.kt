package com.dashwroom.f1telemetry

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dashwroom.f1telemetry.data.history.HistoryDatabase
import com.dashwroom.f1telemetry.data.history.LapEntity
import com.dashwroom.f1telemetry.data.history.LapTraceCodec
import com.dashwroom.f1telemetry.data.history.SessionEntity
import com.dashwroom.f1telemetry.ui.preview.PreviewData
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class HistoryDatabaseTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HistoryDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val dao = db.dao()

    @After
    fun close() = db.close()

    private fun session(uid: Long, at: Long) = SessionEntity(uid, at, at, 10, "Spa", 15, "Race", 2025)

    private fun lap(uid: Long, n: Int, ms: Long, valid: Boolean = true): LapEntity {
        val t = PreviewData.traces()[0]
        return LapEntity(
            sessionUid = uid, lapNumber = n, lapTimeMs = ms, s1Ms = 30_000, s2Ms = 45_000, s3Ms = (ms - 75_000).toInt(),
            valid = valid, tyreVisual = 17, trackLengthM = t.trackLengthM, traces = LapTraceCodec.encode(t),
        )
    }

    @Test
    fun `codec round-trips every channel`() {
        val t = PreviewData.traces()[1]
        val blob = LapTraceCodec.encode(t)
        assertThat(blob.size).isLessThan(t.bins * 7 * 4) // compressed
        val back = LapTraceCodec.decode(lap(1, t.lapNumber, t.lapTimeMs).copy(traces = blob))
        assertThat(back.speedKph).isEqualTo(t.speedKph)
        assertThat(back.throttle).isEqualTo(t.throttle)
        assertThat(back.brake).isEqualTo(t.brake)
        assertThat(back.gear).isEqualTo(t.gear)
        assertThat(back.steer).isEqualTo(t.steer)
        assertThat(back.rpm).isEqualTo(t.rpm)
        assertThat(back.time).isEqualTo(t.time)
    }

    @Test
    fun `sessions aggregate laps and best valid lap`() = runBlocking {
        dao.saveLap(session(7, 1_000), lap(7, 1, 90_000))
        dao.saveLap(session(7, 5_000), lap(7, 2, 88_000, valid = false))
        dao.saveLap(session(7, 6_000), lap(7, 3, 89_000))
        val rows = dao.sessions().first()
        assertThat(rows).hasSize(1)
        assertThat(rows[0].lapCount).isEqualTo(3)
        assertThat(rows[0].bestLapMs).isEqualTo(89_000) // the faster lap was invalid
        assertThat(rows[0].startedAtMillis).isEqualTo(1_000) // first save wins
        assertThat(dao.laps(7).first().map { it.lapNumber }).containsExactly(1, 2, 3).inOrder()
        assertThat(dao.lap(7, 3)!!.lapTimeMs).isEqualTo(89_000)
    }

    @Test
    fun `re-saving a lap replaces it and deleting a session cascades`() = runBlocking {
        dao.saveLap(session(1, 1), lap(1, 1, 90_000))
        dao.saveLap(session(1, 2), lap(1, 1, 91_000))
        assertThat(dao.laps(1).first()).hasSize(1)
        assertThat(dao.lap(1, 1)!!.lapTimeMs).isEqualTo(91_000)
        dao.deleteSession(1)
        assertThat(dao.lap(1, 1)).isNull()
    }

    @Test
    fun `trim keeps the most recently updated sessions`() = runBlocking {
        for (uid in 1L..5L) dao.saveLap(session(uid, uid * 100), lap(uid, 1, 90_000))
        dao.trimSessions(2)
        assertThat(dao.sessions().first().map { it.uid }).containsExactly(5L, 4L).inOrder()
    }
}
