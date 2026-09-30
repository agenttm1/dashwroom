package com.dashwroom.f1telemetry.ui.preview

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import com.dashwroom.f1telemetry.core.model.DamageState
import com.dashwroom.f1telemetry.core.model.DriverHistory
import com.dashwroom.f1telemetry.core.model.DriverState
import com.dashwroom.f1telemetry.core.model.DriverStatus
import com.dashwroom.f1telemetry.core.model.HistoryState
import com.dashwroom.f1telemetry.core.model.LapRecord
import com.dashwroom.f1telemetry.core.model.LapTrace
import com.dashwroom.f1telemetry.core.model.PitStatus
import com.dashwroom.f1telemetry.core.model.PlayerCarState
import com.dashwroom.f1telemetry.core.model.RaceEvent
import com.dashwroom.f1telemetry.core.model.RaceEventType
import com.dashwroom.f1telemetry.core.model.RaceState
import com.dashwroom.f1telemetry.core.model.ResultStatus
import com.dashwroom.f1telemetry.core.model.SectorTimes
import com.dashwroom.f1telemetry.core.model.SessionBests
import com.dashwroom.f1telemetry.core.model.SessionInfo
import com.dashwroom.f1telemetry.core.model.TrackOutline
import com.dashwroom.f1telemetry.core.model.TyreSetState
import com.dashwroom.f1telemetry.core.model.TyreStintRecord
import com.dashwroom.f1telemetry.core.model.WheelState
import com.dashwroom.f1telemetry.core.state.HotTelemetry
import com.dashwroom.f1telemetry.ui.components.MapCars
import com.dashwroom.f1telemetry.ui.screens.overview.OverviewUiState
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Deterministic sample data for @Preview and screenshot tests. */
object PreviewData {
    private val codes = listOf("VER", "NOR", "LEC", "PIA", "HAM", "RUS", "SAI", "ALO", "GAS", "TSU", "ALB", "HUL", "OCO", "STR", "LAW", "BEA", "ANT", "DOO", "BOR", "HAD")
    private val teams = listOf(2, 8, 1, 8, 1, 0, 3, 4, 5, 2, 3, 9, 7, 4, 6, 7, 0, 5, 9, 6)
    const val PLAYER = 5

    fun info(race: Boolean = true) = SessionInfo(
        trackId = 10, trackName = "Spa-Francorchamps", trackLengthM = 7004,
        sessionType = if (race) 15 else 12, sessionTypeName = if (race) "Race" else "Qualifying 3",
        weather = 1, weatherName = "Light cloud", trackTemperatureC = 31, airTemperatureC = 22,
        totalLaps = if (race) 20 else 0, sessionTimeLeftS = 734, sessionDurationS = 900, safetyCarStatus = 0,
        formula = 0, pitSpeedLimitKph = 80, gamePaused = false, networkGame = false,
        pitStopWindowIdealLap = 8, pitStopWindowLatestLap = 12, pitStopRejoinPosition = 9,
        sector2StartM = 2100f, sector3StartM = 4900f,
    )

    fun drivers(race: Boolean = true): List<DriverState> = codes.mapIndexed { i, code ->
        val pos = i + 1
        val best = 106_000L + i * 180L + (i % 3) * 57L
        DriverState(
            vehicleIndex = i, name = code, code = code, teamId = teams[i], teamName = "Team", liveryColour = null,
            raceNumber = 10 + i, isPlayer = i == PLAYER, aiControlled = i != PLAYER, telemetryPublic = true,
            position = pos, gridPosition = ((i * 7) % 20) + 1, currentLap = 9, lapDistance = 3000f - i * 90f,
            totalDistance = 60_000f - i * 90f, currentLapTimeMs = 51_234L + i * 120, lastLapTimeMs = best + 420 + (i % 4) * 110,
            bestLapTimeMs = best, sector = 1, currentSector1Ms = 30_100 + i * 40, currentSector2Ms = 0,
            lastSectorsMs = SectorTimes(30_200 + i * 30, 44_900 + i * 60, 31_400 + i * 20),
            bestSectorsMs = SectorTimes(30_050 + i * 30, 44_700 + i * 60, 31_250 + i * 20),
            liveDeltaToBestMs = if (i % 2 == 0) -120 + i * 30 else 210, intervalMs = if (pos == 1) 0 else 800 + (i * 137) % 1500,
            gapToLeaderMs = i * 1_350, lapsBehindLeader = 0,
            pitStatus = if (i == 14) PitStatus.PITTING else PitStatus.NONE, numPitStops = if (i % 3 == 0) 1 else 0,
            penaltiesSeconds = if (i == 11) 5 else 0, totalWarnings = if (i == 8) 2 else 0, unservedDriveThroughs = 0, unservedStopGoes = 0,
            driverStatus = if (race) DriverStatus.ON_TRACK else if (i % 4 == 0) DriverStatus.FLYING_LAP else DriverStatus.IN_GARAGE,
            resultStatus = ResultStatus.ACTIVE, currentLapInvalid = i == 9,
            tyreVisual = if (i % 3 == 0) 18 else if (i % 3 == 1) 17 else 16, tyreActual = 18, tyreAgeLaps = 3 + i % 7,
            drsOpen = i == 3, drsAllowed = i == 3 || i == 6, fiaFlag = 0, overtakeActive = false,
        )
    }

    fun bests(drivers: List<DriverState>) = SessionBests(
        lapMs = drivers.minOf { it.bestLapTimeMs }, lapVehicle = 0,
        s1Ms = 30_050, s1Vehicle = 0, s2Ms = 44_700, s2Vehicle = 0, s3Ms = 31_250, s3Vehicle = 0,
    )

    fun race(race: Boolean = true): RaceState {
        val d = drivers(race)
        return RaceState(d.toImmutableList(), bests(d), leaderLap = 9, observedPitLaneTimeMs = 21_400)
    }

    fun car() = PlayerCarState(
        vehicleIndex = PLAYER, fuelInTankKg = 42.3f, fuelCapacityKg = 110f, fuelRemainingLaps = 0.6f, fuelMix = 1,
        ersStoreJ = 2_700_000f, ersDeployMode = 1, ersHarvestedThisLapJ = 1_900_000f, ersDeployedThisLapJ = 2_300_000f,
        tyreVisual = 17, tyreActual = 18, tyreAgeLaps = 7,
        wheels = listOf(
            WheelState(96, 101, 22.1f, 520, 18.2f, 2, 0, 0, 0),
            WheelState(99, 104, 22.3f, 540, 19.5f, 3, 0, 0, 0),
            WheelState(91, 97, 23.4f, 610, 23.8f, 6, 4, 0, 0),
            WheelState(113, 108, 23.5f, 640, 26.1f, 7, 5, 0, 0),
        ).toImmutableList(),
        engineTemperatureC = 104, frontBrakeBias = 56, tractionControl = 0,
        damage = DamageState(frontLeftWing = 12, frontRightWing = 3, rearWing = 0, floor = 5, diffuser = 0, sidepod = 2, gearbox = 9, engine = 14),
        tyreSets = List(8) { i ->
            TyreSetState(i, if (i < 3) 16 else if (i < 6) 17 else 18, if (i < 3) 16 else if (i < 6) 17 else 18, if (i == 4) 26 else if (i % 2 == 0) 0 else 8, i != 1, 0, 20 + i, 18, if (i < 3) -600 else if (i < 6) 0 else 450, i == 4)
        }.toImmutableList(),
        fittedTyreSetIndex = 4,
        hasTelemetry = true, hasDamage = true,
    )

    fun outline(): TrackOutline {
        val n = 720
        val x = FloatArray(n)
        val z = FloatArray(n)
        for (i in 0 until n) {
            val t = 2 * PI * i / n
            x[i] = (cos(t) * 420 + cos(3 * t) * 60).toFloat()
            z[i] = (sin(t) * 260 + sin(2 * t) * 90).toFloat()
        }
        return TrackOutline(x, z, BooleanArray(n) { true }, 1f, x.min(), x.max(), z.min(), z.max(), 1)
    }

    fun hot(): HotTelemetry = HotTelemetry().apply {
        speedKph = 287; gear = 7; engineRpm = 11_200; revLightsPercent = 72; throttle = 1f
        drsAllowed = true; ersStoreEnergy = 2_700_000f; currentLapTimeMs = 51_234; lastLapTimeMs = 106_842
        deltaToPersonalBestMs = -184; deltaToSessionBestMs = 312; deltaToLastLapMs = -40; currentLapNum = 9; carPosition = 6
    }

    fun frame(): State<Long> = mutableLongStateOf(0L)

    fun overview(): OverviewUiState {
        val r = race()
        val p = r.drivers[PLAYER]
        return OverviewUiState(
            receiving = true, info = info(), race = r, player = p,
            ahead = r.drivers[PLAYER - 1], behind = r.drivers[PLAYER + 1], car = car(), outline = outline(),
            mapCars = MapCars.from(r, PLAYER),
        )
    }

    fun history(): HistoryState {
        val drivers = (0 until 20).associateWith { v ->
            DriverHistory(
                vehicleIndex = v,
                laps = (1..8).map { lap ->
                    val ms = 106_000L + v * 180 + ((lap * 37 + v * 11) % 900)
                    LapRecord(lap, ms, SectorTimes(30_100 + lap * 10, 44_800, (ms - 74_900 - lap * 10).toInt()), lap != 4 || v != PLAYER, true, true, true, if (lap <= 4) 16 else 17)
                }.toImmutableList(),
                stints = persistentListOf(TyreStintRecord(4, 18, 16), TyreStintRecord(null, 18, 17)),
                bestLapNumber = 6, bestSector1LapNumber = 2, bestSector2LapNumber = 6, bestSector3LapNumber = 7,
            )
        }
        val positions = (0 until 20).associateWith { v ->
            (1..9).map { lap -> (((v + lap / 3 + (if (v % 2 == 0) lap % 2 else 0)) % 20) + 1) }.toImmutableList()
        }
        return HistoryState(drivers.toImmutableMap(), positions.toImmutableMap())
    }

    fun events() = persistentListOf(
        RaceEvent(5, 812f, 9, RaceEventType.OVERTAKE, "PIA overtakes LEC", 3, 2),
        RaceEvent(4, 790f, 9, RaceEventType.DRS_ENABLED, "DRS enabled"),
        RaceEvent(3, 700f, 8, RaceEventType.FASTEST_LAP, "Fastest lap VER 1:46.000", 0),
        RaceEvent(2, 640f, 8, RaceEventType.PENALTY, "HUL 5 s time penalty — corner cutting", 11),
        RaceEvent(1, 120f, 1, RaceEventType.LIGHTS_OUT, "Lights out"),
    )

    fun traces(): List<LapTrace> = listOf(6, 7).map { lap ->
        val n = 512
        val speed = FloatArray(n) { i -> (220 + 90 * sin(i * 0.05 + lap * 0.1) + 20 * cos(i * 0.21)).toFloat() }
        LapTrace(
            sessionUid = 1, lapNumber = lap, lapTimeMs = 106_000L + lap * 100, sectorsMs = SectorTimes(30_100, 44_800, 31_100 + lap * 100),
            valid = true, trackLengthM = 7004f, tyreVisual = 17,
            speedKph = speed,
            throttle = FloatArray(n) { i -> if (sin(i * 0.05 + lap * 0.1) > -0.3) 1f else 0.1f },
            brake = FloatArray(n) { i -> if (sin(i * 0.05 + lap * 0.1) < -0.6) 0.9f else 0f },
            gear = FloatArray(n) { i -> (speed[i] / 45f).coerceIn(1f, 8f).toInt().toFloat() },
            steer = FloatArray(n) { i -> (0.6 * sin(i * 0.11)).toFloat() },
            rpm = FloatArray(n) { i -> 9_000f + (speed[i] % 45f) * 60f },
            time = FloatArray(n) { i -> i * (106f + lap * 0.1f) / n },
        )
    }
}
