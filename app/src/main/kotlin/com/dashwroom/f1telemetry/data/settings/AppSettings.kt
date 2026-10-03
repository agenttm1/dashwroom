package com.dashwroom.f1telemetry.data.settings

import com.dashwroom.f1telemetry.core.model.SourceKind
import com.dashwroom.f1telemetry.core.protocol.PacketFormat
import com.dashwroom.f1telemetry.replay.mock.MockSessionMode

/** Everything the user can configure. Immutable snapshot of DataStore. */
data class AppSettings(
    val udpPort: Int = DEFAULT_PORT,
    val dataSource: SourceKind = SourceKind.LIVE,
    val mockFormat: PacketFormat = PacketFormat.F1_25,
    val mockSession: MockSessionMode = MockSessionMode.RACE,
    /** Absolute path of the recording to replay; null = most recent. */
    val replayFile: String? = null,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val speedUnit: SpeedUnit = SpeedUnit.KPH,
    val temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
    val uiDensity: UiDensity = UiDensity.NORMAL,
    val keepScreenOn: Boolean = true,
    val startOnBoot: Boolean = false,
    val deltaReference: DeltaReference = DeltaReference.PERSONAL_BEST,
    val orientationLocks: Map<ScreenKey, OrientationLock> = emptyMap(),
    val debugHud: Boolean = false,
    val drivePreset: DrivePreset = DrivePreset.AUTO,
    /** Flash yellow/red/green flags and the safety car over the screen. */
    val flagFlashes: Boolean = true,
) {
    fun orientationFor(screen: ScreenKey): OrientationLock = orientationLocks[screen] ?: screen.defaultOrientation

    companion object {
        const val DEFAULT_PORT = 20777
    }
}

enum class ThemeMode { SYSTEM, DARK, LIGHT }

enum class SpeedUnit(val label: String) { KPH("km/h"), MPH("mph") }

enum class TemperatureUnit(val label: String) { CELSIUS("°C"), FAHRENHEIT("°F") }

/** Global UI scale, for tablets mounted far from the driver. Respects the system font scale. */
enum class UiDensity(val label: String, val scale: Float) {
    COMPACT("Compact", 0.87f),
    NORMAL("Normal", 1f),
    LARGE("Large", 1.2f),
}

enum class DeltaReference(val label: String) {
    PERSONAL_BEST("Personal best"),
    SESSION_BEST("Session best"),
    LAST_LAP("Last lap"),
}

/**
 * What the Drive screen shows. AUTO follows the session type (race → RACE, qualifying and
 * practice → QUALI, time trial → TIME_TRIAL).
 */
enum class DrivePreset(val label: String) {
    AUTO("Auto"),
    RACE("Race"),
    QUALI("Quali"),
    TIME_TRIAL("Time trial"),
    MINIMAL("Minimal"),
}

enum class OrientationLock(val label: String) { UNLOCKED("Auto"), PORTRAIT("Portrait"), LANDSCAPE("Landscape") }

/** Screens with a per-screen orientation preference. Race and Qualifying are landscape-first. */
enum class ScreenKey(val label: String, val defaultOrientation: OrientationLock) {
    CONNECT("Connect", OrientationLock.UNLOCKED),
    DRIVE("Drive", OrientationLock.UNLOCKED),
    OVERVIEW("Overview", OrientationLock.UNLOCKED),
    RACE("Race", OrientationLock.UNLOCKED),
    QUALIFYING("Qualifying", OrientationLock.UNLOCKED),
    CAR("Car & Tyres", OrientationLock.UNLOCKED),
    ANALYSIS("Lap Analysis", OrientationLock.UNLOCKED),
    SETTINGS("Settings", OrientationLock.UNLOCKED),
}
