package com.dashwroom.f1telemetry.core.packet

import com.dashwroom.f1telemetry.core.protocol.PacketId

/** Packet 1 — session details, sent twice a second. */
class SessionPacket : F1Packet {
    override val packetId: Int = PacketId.SESSION
    override val header = PacketHeader()

    /** 0 = clear, 1 = light cloud, 2 = overcast, 3 = light rain, 4 = heavy rain, 5 = storm. */
    var weather = 0
    var trackTemperature = 0
    var airTemperature = 0
    var totalLaps = 0
    var trackLength = 0
    var sessionType = 0

    /** -1 for unknown. */
    var trackId = -1
    var formula = 0
    var sessionTimeLeft = 0
    var sessionDuration = 0
    var pitSpeedLimit = 0
    var gamePaused = false
    var isSpectating = false
    var spectatorCarIndex = 0
    var sliProNativeSupport = false
    var numMarshalZones = 0
    val marshalZoneStart = FloatArray(MAX_MARSHAL_ZONES)

    /** -1 = invalid/unknown, 0 = none, 1 = green, 2 = blue, 3 = yellow. */
    val marshalZoneFlag = IntArray(MAX_MARSHAL_ZONES)

    /** 0 = none, 1 = full, 2 = virtual, 3 = formation lap. */
    var safetyCarStatus = 0
    var networkGame = false
    var numWeatherForecastSamples = 0
    val weatherForecast = Array(MAX_WEATHER_SAMPLES) { WeatherForecastSample() }
    var forecastAccuracy = 0
    var aiDifficulty = 0
    var seasonLinkIdentifier = 0L
    var weekendLinkIdentifier = 0L
    var sessionLinkIdentifier = 0L
    var pitStopWindowIdealLap = 0
    var pitStopWindowLatestLap = 0
    var pitStopRejoinPosition = 0
    var steeringAssist = 0
    var brakingAssist = 0
    var gearboxAssist = 0
    var pitAssist = 0
    var pitReleaseAssist = 0
    var ersAssist = 0
    var drsAssist = 0
    var dynamicRacingLine = 0
    var dynamicRacingLineType = 0
    var gameMode = 0
    var ruleSet = 0
    var timeOfDayMinutes = 0L
    var sessionLength = 0
    var speedUnitsLeadPlayer = 0
    var temperatureUnitsLeadPlayer = 0
    var speedUnitsSecondaryPlayer = 0
    var temperatureUnitsSecondaryPlayer = 0
    var numSafetyCarPeriods = 0
    var numVirtualSafetyCarPeriods = 0
    var numRedFlagPeriods = 0
    var equalCarPerformance = 0
    var recoveryMode = 0
    var flashbackLimit = 0
    var surfaceType = 0
    var lowFuelMode = 0
    var raceStarts = 0
    var tyreTemperature = 0
    var pitLaneTyreSim = 0
    var carDamage = 0
    var carDamageRate = 0
    var collisions = 0
    var collisionsOffForFirstLapOnly = 0
    var mpUnsafePitRelease = 0
    var mpOffForGriefing = 0
    var cornerCuttingStringency = 0
    var parcFermeRules = 0
    var pitStopExperience = 0
    var safetyCar = 0
    var safetyCarExperience = 0
    var formationLap = 0
    var formationLapExperience = 0
    var redFlags = 0
    var affectsLicenceLevelSolo = 0
    var affectsLicenceLevelMp = 0
    var numSessionsInWeekend = 0
    val weekendStructure = IntArray(MAX_SESSIONS_IN_WEEKEND)
    var sector2LapDistanceStart = 0f
    var sector3LapDistanceStart = 0f

    // ---- 2026 Season Pack only (left at defaults for 2025 packets) ----
    /** 0 = full, 1 = partial. */
    var activeAeroTrackStatus = 0
    var numActiveAeroZonesFull = 0
    val activeAeroZonesFullStart = FloatArray(MAX_ACTIVE_AERO_ZONES)
    val activeAeroZonesFullEnd = FloatArray(MAX_ACTIVE_AERO_ZONES)
    var numActiveAeroZonesPartial = 0
    val activeAeroZonesPartialStart = FloatArray(MAX_ACTIVE_AERO_ZONES)
    val activeAeroZonesPartialEnd = FloatArray(MAX_ACTIVE_AERO_ZONES)
    var numDrsZones = 0
    val drsZoneStart = FloatArray(MAX_DRS_ZONES)
    val drsZoneEnd = FloatArray(MAX_DRS_ZONES)

    /** Seconds; 0 if assisted starts. */
    var startReactionTime = 0f
    var antiLockBrakesAssist = 0
    var tractionControlAssist = 0
    var dynamicRacingLineHiVis = 0
    var dynamicRacingLineColourBlind = 0
    var recurringRewindPrompt = 0

    class WeatherForecastSample {
        var sessionType = 0
        var timeOffsetMinutes = 0
        var weather = 0
        var trackTemperature = 0

        /** 0 = up, 1 = down, 2 = no change. */
        var trackTemperatureChange = 0
        var airTemperature = 0
        var airTemperatureChange = 0
        var rainPercentage = 0
    }

    companion object {
        const val MAX_MARSHAL_ZONES = 21
        const val MAX_WEATHER_SAMPLES = 64
        const val MAX_SESSIONS_IN_WEEKEND = 12
        const val MAX_ACTIVE_AERO_ZONES = 8
        const val MAX_DRS_ZONES = 4
    }
}
