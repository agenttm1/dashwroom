package com.dashwroom.f1telemetry.replay

import com.dashwroom.f1telemetry.core.ingest.IngestStats
import com.dashwroom.f1telemetry.core.ingest.TelemetryPipeline
import com.dashwroom.f1telemetry.core.model.RaceEventType
import com.dashwroom.f1telemetry.core.protocol.PacketFormat
import com.dashwroom.f1telemetry.core.state.HotTelemetry
import com.dashwroom.f1telemetry.core.state.TelemetryStore
import com.dashwroom.f1telemetry.replay.mock.MockSessionMode
import com.dashwroom.f1telemetry.replay.mock.MockTelemetryEmitter
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.lang.management.ManagementFactory

/** Mock sessions through the real pipeline → every domain model is populated and consistent. */
class DomainIntegrationTest {
    private val frameNanos = 16_666_667L

    private class Harness(format: PacketFormat, mode: MockSessionMode, laps: Int = 8) {
        val hot = HotTelemetry()
        val stats = IngestStats()
        val store = TelemetryStore(hot)
        val pipeline = TelemetryPipeline(stats, store)
        val emitter = MockTelemetryEmitter(format, totalLaps = laps, seed = 9, mode = mode)
        var nanos = 1_000_000_000L
        var deltaSamples = 0

        fun run(seconds: Int) = repeat(seconds * 60) {
            emitter.emitFrame(pipeline, nanos)
            nanos += 16_666_667L
            if (hot.hasDelta(hot.deltaToPersonalBestMs)) deltaSamples++
        }
    }

    @Test
    fun `race model is complete in both formats`() {
        for (format in PacketFormat.entries) {
            val h = Harness(format, MockSessionMode.RACE)
            h.run(5 * 60) // 5 minutes: ~3 laps, safety car window not yet, contact on lap 3
            val race = h.store.race.value
            val cars = if (format == PacketFormat.F1_25) 20 else 22
            assertThat(race.drivers).hasSize(cars)
            assertThat(race.drivers.map { it.position }).isEqualTo((1..cars).toList())
            assertThat(race.drivers.count { it.isPlayer }).isEqualTo(1)
            assertThat(race.drivers.map { it.code }.toSet().size).isEqualTo(cars) // unique timing codes
            assertThat(race.player!!.code).isNotEmpty()
            assertThat(race.bests.lapMs).isGreaterThan(80_000L)
            assertThat(race.drivers.filter { it.position > 1 }.all { it.intervalMs >= 0 }).isTrue()
            assertThat(race.drivers.first().gapToLeaderMs).isEqualTo(0)
            assertThat(race.drivers.any { it.bestSectorsMs != null }).isTrue()

            val history = h.store.history.value
            assertThat(history.drivers.size).isAtLeast(cars - 1)
            assertThat(history.drivers.values.first().laps).isNotEmpty()
            assertThat(history.lapPositions).isNotEmpty()

            val player = h.store.player.value
            assertThat(player.wheels).hasSize(4)
            assertThat(player.fuelInTankKg).isGreaterThan(0f)
            assertThat(player.ersStoreJ).isGreaterThan(0f)
            assertThat(player.tyreSets).hasSize(20)
            assertThat(player.tyreSets.count { it.fitted }).isEqualTo(1)
            assertThat(player.chassis).isNotNull()
            assertThat(player.damage.frontLeftWing).isEqualTo(14) // scripted contact on lap 3

            val events = h.store.events.value.map { it.type }
            assertThat(events).containsAtLeast(RaceEventType.SESSION_STARTED, RaceEventType.LIGHTS_OUT, RaceEventType.FASTEST_LAP)
            assertThat(h.store.events.value.first().id).isGreaterThan(h.store.events.value.last().id) // newest first

            val outline = h.store.trackOutline.value!!
            assertThat(outline.coverage).isGreaterThan(0.95f)
            assertThat(outline.maxX - outline.minX).isGreaterThan(500f)

            assertThat(h.store.laps.value.size).isAtLeast(1)
            val trace = h.store.laps.value.last()
            assertThat(trace.speedKph.max()).isGreaterThan(280f)
            assertThat(trace.time.last()).isWithin(3f).of(trace.lapTimeMs / 1000f)
            assertThat(h.deltaSamples).isGreaterThan(1000) // live delta to PB available after lap 1

            assertThat(h.stats.totalSizeMismatches()).isEqualTo(0)
            assertThat(h.store.session.value.info!!.isRace).isTrue()
        }
    }

    @Test
    fun `a full race ends with a final classification and complete histories`() {
        val h = Harness(PacketFormat.F1_25, MockSessionMode.RACE, laps = 6)
        var seconds = 0
        while (h.store.history.value.finalClassification.isEmpty() && seconds < 15 * 60) {
            h.run(1)
            seconds++
        }
        val final = h.store.history.value.finalClassification
        assertThat(final).hasSize(20)
        assertThat(final.first().position).isEqualTo(1)
        assertThat(final.first().points).isEqualTo(25)
        val events = h.store.events.value.map { it.type }
        assertThat(events).containsAtLeast(RaceEventType.CHEQUERED_FLAG, RaceEventType.RACE_WINNER, RaceEventType.SAFETY_CAR, RaceEventType.RETIREMENT, RaceEventType.PENALTY, RaceEventType.OVERTAKE)
        val winner = h.store.history.value.drivers[final.first().vehicleIndex]!!
        assertThat(winner.laps.size).isEqualTo(6)
        assertThat(winner.stints.size).isEqualTo(2) // one pit stop → two stints
    }

    @Test
    fun `qualifying orders by best lap with flying-lap statuses`() {
        val h = Harness(PacketFormat.F1_25_SEASON_2026, MockSessionMode.QUALIFYING)
        h.run(7 * 60)
        val race = h.store.race.value
        val timed = race.drivers.filter { it.bestLapTimeMs > 0 }
        assertThat(timed.size).isAtLeast(15)
        assertThat(timed.map { it.bestLapTimeMs }).isInOrder()
        assertThat(race.drivers.map { it.driverStatus }.toSet().size).isAtLeast(2)
        assertThat(h.store.session.value.info!!.isQualifying).isTrue()
        assertThat(race.bests.s1Ms).isGreaterThan(0)
    }

    @Test
    fun `measured allocation of 10 Hz model publishing`() {
        val bean = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        val h = Harness(PacketFormat.F1_25, MockSessionMode.RACE)
        h.run(90) // warm up past the start
        val thread = Thread.currentThread().id
        val before = bean.getThreadAllocatedBytes(thread)
        h.run(60)
        val perSecond = (bean.getThreadAllocatedBytes(thread) - before) / 60
        println("ALLOC mock+pipeline+10Hz models: ${perSecond / 1024} KiB per second of racing")
        // Only the ≤10 Hz immutable models allocate (~130 KiB/s measured); guard against regressions.
        assertThat(perSecond).isLessThan(512L * 1024)
    }
}
