package com.dashwroom.f1telemetry.replay

import com.dashwroom.f1telemetry.core.packet.EventCode
import com.dashwroom.f1telemetry.core.protocol.PacketFormat
import com.dashwroom.f1telemetry.replay.mock.MockRace
import com.dashwroom.f1telemetry.replay.mock.MockTrack
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MockRaceTest {
    @Test
    fun `procedural track is a plausible circuit`() {
        val track = MockTrack()
        assertThat(track.idealLapTimeSeconds()).isIn(com.google.common.collect.Range.closed(80f, 110f))
        assertThat(track.minSpeed() * 3.6f).isGreaterThan(25f)
        assertThat(track.drsZones.size).isEqualTo(4)
        // Closed loop: last sample connects back to the first.
        val dx = track.x[0] - track.x[track.count - 1]
        val dz = track.z[0] - track.z[track.count - 1]
        assertThat(kotlin.math.hypot(dx, dz)).isLessThan(track.step * 1.5f)
    }

    @Test
    fun `a full race produces every scripted event and a valid classification`() {
        for (format in PacketFormat.entries) {
            val race = MockRace(format, totalLaps = 12, seed = 7)
            val seen = mutableMapOf<Int, Int>()
            val safetyCarPhases = mutableSetOf<Int>()
            var frames = 0
            while (race.finishedAt < 0f && frames < 60 * 60 * 40) {
                race.step(1f / 60f)
                frames++
                while (true) {
                    val e = race.events.poll() ?: break
                    seen[e.code] = (seen[e.code] ?: 0) + 1
                    if (e.code == EventCode.SAFETY_CAR) safetyCarPhases += e.safetyCarEventType
                }
                // Positions are always a permutation of 1..n.
                assertThat(race.cars.position.take(race.carCount).sorted()).isEqualTo((1..race.carCount).toList())
            }
            assertThat(race.finishedAt).isAtLeast(0f)
            for (code in listOf(
                EventCode.SESSION_STARTED, EventCode.START_LIGHTS, EventCode.LIGHTS_OUT, EventCode.DRS_ENABLED,
                EventCode.FASTEST_LAP, EventCode.OVERTAKE, EventCode.SAFETY_CAR, EventCode.DRS_DISABLED,
                EventCode.PENALTY, EventCode.RETIREMENT, EventCode.CHEQUERED_FLAG, EventCode.RACE_WINNER,
                EventCode.SESSION_ENDED,
            )) {
                assertThat(seen).containsKey(code)
            }
            assertThat(seen[EventCode.START_LIGHTS]).isEqualTo(5)
            assertThat(safetyCarPhases).containsExactly(0, 1, 2, 3)
            val c = race.cars
            val retired = (0 until race.carCount).count { c.resultStatus[it] == MockRace.RESULT_RETIRED }
            val finished = (0 until race.carCount).count { c.resultStatus[it] == MockRace.RESULT_FINISHED }
            assertThat(retired).isEqualTo(1)
            assertThat(finished).isEqualTo(race.carCount - 1)
            // Everyone but the retiree pitted exactly once.
            assertThat((0 until race.carCount).count { c.pitStops[it] == 1 }).isAtLeast(race.carCount - 2)
            // Lap times look like laps of this track.
            val best = race.sessionBestLapMs / 1000f
            assertThat(best).isIn(com.google.common.collect.Range.closed(80f, 115f))
        }
    }
}
