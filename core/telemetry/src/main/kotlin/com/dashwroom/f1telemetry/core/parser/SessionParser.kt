package com.dashwroom.f1telemetry.core.parser

import com.dashwroom.f1telemetry.core.packet.SessionPacket
import com.dashwroom.f1telemetry.core.protocol.PacketFormat
import com.dashwroom.f1telemetry.core.protocol.f32
import com.dashwroom.f1telemetry.core.protocol.flag
import com.dashwroom.f1telemetry.core.protocol.i8
import com.dashwroom.f1telemetry.core.protocol.u16
import com.dashwroom.f1telemetry.core.protocol.u32
import com.dashwroom.f1telemetry.core.protocol.u8
import java.nio.ByteBuffer

/**
 * Packet 1 — `PacketSessionData`. 2025: 753 bytes. 2026: 926 bytes (same prefix + aero/DRS block).
 * Offsets below are absolute (from the start of the datagram, header included).
 *
 * ```
 * off  type                        field
 *  29  uint8                       m_weather
 *  30  int8                        m_trackTemperature (°C)
 *  31  int8                        m_airTemperature (°C)
 *  32  uint8                       m_totalLaps
 *  33  uint16                      m_trackLength (m)
 *  35  uint8                       m_sessionType
 *  36  int8                        m_trackId (-1 unknown)
 *  37  uint8                       m_formula
 *  38  uint16                      m_sessionTimeLeft (s)
 *  40  uint16                      m_sessionDuration (s)
 *  42  uint8                       m_pitSpeedLimit (km/h)
 *  43  uint8                       m_gamePaused
 *  44  uint8                       m_isSpectating
 *  45  uint8                       m_spectatorCarIndex
 *  46  uint8                       m_sliProNativeSupport
 *  47  uint8                       m_numMarshalZones
 *  48  MarshalZone[21] ×5 = 105    { float m_zoneStart @+0; int8 m_zoneFlag @+4 }
 * 153  uint8                       m_safetyCarStatus
 * 154  uint8                       m_networkGame
 * 155  uint8                       m_numWeatherForecastSamples
 * 156  WeatherForecastSample[64] ×8 = 512
 *        { u8 sessionType@0, u8 timeOffset@1, u8 weather@2, i8 trackTemp@3, i8 trackTempChange@4,
 *          i8 airTemp@5, i8 airTempChange@6, u8 rainPercentage@7 }
 * 668  uint8                       m_forecastAccuracy
 * 669  uint8                       m_aiDifficulty
 * 670  uint32                      m_seasonLinkIdentifier
 * 674  uint32                      m_weekendLinkIdentifier
 * 678  uint32                      m_sessionLinkIdentifier
 * 682  uint8                       m_pitStopWindowIdealLap
 * 683  uint8                       m_pitStopWindowLatestLap
 * 684  uint8                       m_pitStopRejoinPosition
 * 685  uint8 ×11                   m_steeringAssist, m_brakingAssist, m_gearboxAssist, m_pitAssist,
 *                                  m_pitReleaseAssist, m_ERSAssist, m_DRSAssist, m_dynamicRacingLine,
 *                                  m_dynamicRacingLineType, m_gameMode, m_ruleSet   (685..695)
 * 696  uint32                      m_timeOfDay (minutes since midnight)
 * 700  uint8                       m_sessionLength
 * 701  uint8 ×4                    m_speedUnitsLeadPlayer, m_temperatureUnitsLeadPlayer,
 *                                  m_speedUnitsSecondaryPlayer, m_temperatureUnitsSecondaryPlayer
 * 705  uint8 ×3                    m_numSafetyCarPeriods, m_numVirtualSafetyCarPeriods, m_numRedFlagPeriods
 * 708  uint8 ×24                   m_equalCarPerformance … m_affectsLicenceLevelMP   (708..731, see below)
 * 732  uint8                       m_numSessionsInWeekend
 * 733  uint8[12]                   m_weekendStructure
 * 745  float                       m_sector2LapDistanceStart (m)
 * 749  float                       m_sector3LapDistanceStart (m)
 * 753  — end of 2025 packet —
 * 753  uint8                       m_activeAeroTrackStatus          (2026 only from here)
 * 754  uint8                       m_numActiveAeroZonesFull
 * 755  ActiveAeroZone[8] ×8 = 64   { float start @+0; float end @+4 }
 * 819  uint8                       m_numActiveAeroZonesPartial
 * 820  ActiveAeroZone[8] ×8 = 64
 * 884  uint8                       m_numDRSZones
 * 885  DRSZone[4] ×8 = 32          { float start @+0; float end @+4 }
 * 917  float                       m_startReactionTime (s)
 * 921  uint8 ×5                    m_antiLockBrakesAssist, m_tractionControlAssist,
 *                                  m_dynamicRacingLineHiVis, m_dynamicRacingLineColourBlind,
 *                                  m_recurringRewindPrompt
 * 926  — end of 2026 packet —
 * ```
 */
internal object SessionParser {
    private const val MARSHAL_ZONES = 48
    private const val MARSHAL_ZONE_STRIDE = 5
    private const val WEATHER_SAMPLES = 156
    private const val WEATHER_SAMPLE_STRIDE = 8
    private const val WEEKEND_STRUCTURE = 733
    private const val AERO_FULL = 755
    private const val AERO_PARTIAL = 820
    private const val DRS_ZONES = 885
    private const val ZONE_STRIDE = 8

    fun parse(buf: ByteBuffer, out: SessionPacket) {
        out.weather = buf.u8(29)
        out.trackTemperature = buf.i8(30)
        out.airTemperature = buf.i8(31)
        out.totalLaps = buf.u8(32)
        out.trackLength = buf.u16(33)
        out.sessionType = buf.u8(35)
        out.trackId = buf.i8(36)
        out.formula = buf.u8(37)
        out.sessionTimeLeft = buf.u16(38)
        out.sessionDuration = buf.u16(40)
        out.pitSpeedLimit = buf.u8(42)
        out.gamePaused = buf.flag(43)
        out.isSpectating = buf.flag(44)
        out.spectatorCarIndex = buf.u8(45)
        out.sliProNativeSupport = buf.flag(46)
        out.numMarshalZones = buf.u8(47).coerceAtMost(SessionPacket.MAX_MARSHAL_ZONES)
        for (z in 0 until SessionPacket.MAX_MARSHAL_ZONES) {
            val b = MARSHAL_ZONES + z * MARSHAL_ZONE_STRIDE
            out.marshalZoneStart[z] = buf.f32(b)
            out.marshalZoneFlag[z] = buf.i8(b + 4)
        }
        out.safetyCarStatus = buf.u8(153)
        out.networkGame = buf.flag(154)
        out.numWeatherForecastSamples = buf.u8(155).coerceAtMost(SessionPacket.MAX_WEATHER_SAMPLES)
        for (s in 0 until SessionPacket.MAX_WEATHER_SAMPLES) {
            val b = WEATHER_SAMPLES + s * WEATHER_SAMPLE_STRIDE
            val w = out.weatherForecast[s]
            w.sessionType = buf.u8(b)
            w.timeOffsetMinutes = buf.u8(b + 1)
            w.weather = buf.u8(b + 2)
            w.trackTemperature = buf.i8(b + 3)
            w.trackTemperatureChange = buf.i8(b + 4)
            w.airTemperature = buf.i8(b + 5)
            w.airTemperatureChange = buf.i8(b + 6)
            w.rainPercentage = buf.u8(b + 7)
        }
        out.forecastAccuracy = buf.u8(668)
        out.aiDifficulty = buf.u8(669)
        out.seasonLinkIdentifier = buf.u32(670)
        out.weekendLinkIdentifier = buf.u32(674)
        out.sessionLinkIdentifier = buf.u32(678)
        out.pitStopWindowIdealLap = buf.u8(682)
        out.pitStopWindowLatestLap = buf.u8(683)
        out.pitStopRejoinPosition = buf.u8(684)
        out.steeringAssist = buf.u8(685)
        out.brakingAssist = buf.u8(686)
        out.gearboxAssist = buf.u8(687)
        out.pitAssist = buf.u8(688)
        out.pitReleaseAssist = buf.u8(689)
        out.ersAssist = buf.u8(690)
        out.drsAssist = buf.u8(691)
        out.dynamicRacingLine = buf.u8(692)
        out.dynamicRacingLineType = buf.u8(693)
        out.gameMode = buf.u8(694)
        out.ruleSet = buf.u8(695)
        out.timeOfDayMinutes = buf.u32(696)
        out.sessionLength = buf.u8(700)
        out.speedUnitsLeadPlayer = buf.u8(701)
        out.temperatureUnitsLeadPlayer = buf.u8(702)
        out.speedUnitsSecondaryPlayer = buf.u8(703)
        out.temperatureUnitsSecondaryPlayer = buf.u8(704)
        out.numSafetyCarPeriods = buf.u8(705)
        out.numVirtualSafetyCarPeriods = buf.u8(706)
        out.numRedFlagPeriods = buf.u8(707)
        out.equalCarPerformance = buf.u8(708)
        out.recoveryMode = buf.u8(709)
        out.flashbackLimit = buf.u8(710)
        out.surfaceType = buf.u8(711)
        out.lowFuelMode = buf.u8(712)
        out.raceStarts = buf.u8(713)
        out.tyreTemperature = buf.u8(714)
        out.pitLaneTyreSim = buf.u8(715)
        out.carDamage = buf.u8(716)
        out.carDamageRate = buf.u8(717)
        out.collisions = buf.u8(718)
        out.collisionsOffForFirstLapOnly = buf.u8(719)
        out.mpUnsafePitRelease = buf.u8(720)
        out.mpOffForGriefing = buf.u8(721)
        out.cornerCuttingStringency = buf.u8(722)
        out.parcFermeRules = buf.u8(723)
        out.pitStopExperience = buf.u8(724)
        out.safetyCar = buf.u8(725)
        out.safetyCarExperience = buf.u8(726)
        out.formationLap = buf.u8(727)
        out.formationLapExperience = buf.u8(728)
        out.redFlags = buf.u8(729)
        out.affectsLicenceLevelSolo = buf.u8(730)
        out.affectsLicenceLevelMp = buf.u8(731)
        out.numSessionsInWeekend = buf.u8(732).coerceAtMost(SessionPacket.MAX_SESSIONS_IN_WEEKEND)
        for (s in 0 until SessionPacket.MAX_SESSIONS_IN_WEEKEND) {
            out.weekendStructure[s] = buf.u8(WEEKEND_STRUCTURE + s)
        }
        out.sector2LapDistanceStart = buf.f32(745)
        out.sector3LapDistanceStart = buf.f32(749)

        if (out.format == PacketFormat.F1_25_SEASON_2026) parse2026Block(buf, out) else clear2026Block(out)
    }

    private fun parse2026Block(buf: ByteBuffer, out: SessionPacket) {
        out.activeAeroTrackStatus = buf.u8(753)
        out.numActiveAeroZonesFull = buf.u8(754).coerceAtMost(SessionPacket.MAX_ACTIVE_AERO_ZONES)
        out.numActiveAeroZonesPartial = buf.u8(819).coerceAtMost(SessionPacket.MAX_ACTIVE_AERO_ZONES)
        for (z in 0 until SessionPacket.MAX_ACTIVE_AERO_ZONES) {
            out.activeAeroZonesFullStart[z] = buf.f32(AERO_FULL + z * ZONE_STRIDE)
            out.activeAeroZonesFullEnd[z] = buf.f32(AERO_FULL + z * ZONE_STRIDE + 4)
            out.activeAeroZonesPartialStart[z] = buf.f32(AERO_PARTIAL + z * ZONE_STRIDE)
            out.activeAeroZonesPartialEnd[z] = buf.f32(AERO_PARTIAL + z * ZONE_STRIDE + 4)
        }
        out.numDrsZones = buf.u8(884).coerceAtMost(SessionPacket.MAX_DRS_ZONES)
        for (z in 0 until SessionPacket.MAX_DRS_ZONES) {
            out.drsZoneStart[z] = buf.f32(DRS_ZONES + z * ZONE_STRIDE)
            out.drsZoneEnd[z] = buf.f32(DRS_ZONES + z * ZONE_STRIDE + 4)
        }
        out.startReactionTime = buf.f32(917)
        out.antiLockBrakesAssist = buf.u8(921)
        out.tractionControlAssist = buf.u8(922)
        out.dynamicRacingLineHiVis = buf.u8(923)
        out.dynamicRacingLineColourBlind = buf.u8(924)
        out.recurringRewindPrompt = buf.u8(925)
    }

    private fun clear2026Block(out: SessionPacket) {
        out.activeAeroTrackStatus = 0
        out.numActiveAeroZonesFull = 0
        out.numActiveAeroZonesPartial = 0
        out.numDrsZones = 0
        out.startReactionTime = 0f
        out.antiLockBrakesAssist = 0
        out.tractionControlAssist = 0
        out.dynamicRacingLineHiVis = 0
        out.dynamicRacingLineColourBlind = 0
        out.recurringRewindPrompt = 0
    }
}
