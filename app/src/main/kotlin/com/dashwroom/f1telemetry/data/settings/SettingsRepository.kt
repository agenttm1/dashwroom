package com.dashwroom.f1telemetry.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.dashwroom.f1telemetry.core.model.SourceKind
import com.dashwroom.f1telemetry.core.protocol.PacketFormat
import com.dashwroom.f1telemetry.replay.mock.MockSessionMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** DataStore-backed settings. Unknown/corrupt values fall back to defaults instead of crashing. */
@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val settings: Flow<AppSettings> = dataStore.data.map(::read).distinctUntilChanged()

    suspend fun current(): AppSettings = settings.first()

    suspend fun setUdpPort(port: Int) = edit { it[Keys.PORT] = port.coerceIn(1024, 65_535) }
    suspend fun setDataSource(source: SourceKind) = edit { it[Keys.SOURCE] = source.name }
    suspend fun setMockFormat(format: PacketFormat) = edit { it[Keys.MOCK_FORMAT] = format.name }
    suspend fun setMockSession(mode: MockSessionMode) = edit { it[Keys.MOCK_SESSION] = mode.name }
    suspend fun setReplayFile(path: String?) = edit { if (path == null) it.remove(Keys.REPLAY_FILE) else it[Keys.REPLAY_FILE] = path }
    suspend fun setThemeMode(mode: ThemeMode) = edit { it[Keys.THEME] = mode.name }
    suspend fun setSpeedUnit(unit: SpeedUnit) = edit { it[Keys.SPEED_UNIT] = unit.name }
    suspend fun setTemperatureUnit(unit: TemperatureUnit) = edit { it[Keys.TEMP_UNIT] = unit.name }
    suspend fun setUiDensity(density: UiDensity) = edit { it[Keys.DENSITY] = density.name }
    suspend fun setKeepScreenOn(enabled: Boolean) = edit { it[Keys.KEEP_SCREEN_ON] = enabled }
    suspend fun setStartOnBoot(enabled: Boolean) = edit { it[Keys.START_ON_BOOT] = enabled }
    suspend fun setDeltaReference(reference: DeltaReference) = edit { it[Keys.DELTA_REFERENCE] = reference.name }
    suspend fun setDebugHud(enabled: Boolean) = edit { it[Keys.DEBUG_HUD] = enabled }
    suspend fun setDrivePreset(preset: DrivePreset) = edit { it[Keys.DRIVE_PRESET] = preset.name }
    suspend fun setOrientationLock(screen: ScreenKey, lock: OrientationLock) =
        edit { it[orientationKey(screen)] = lock.name }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        dataStore.edit { block(it) }
    }

    private fun read(p: Preferences): AppSettings = AppSettings(
        udpPort = p[Keys.PORT] ?: AppSettings.DEFAULT_PORT,
        dataSource = enumOr(p[Keys.SOURCE], SourceKind.LIVE),
        mockFormat = enumOr(p[Keys.MOCK_FORMAT], PacketFormat.F1_25),
        mockSession = enumOr(p[Keys.MOCK_SESSION], MockSessionMode.RACE),
        replayFile = p[Keys.REPLAY_FILE],
        themeMode = enumOr(p[Keys.THEME], ThemeMode.DARK),
        speedUnit = enumOr(p[Keys.SPEED_UNIT], SpeedUnit.KPH),
        temperatureUnit = enumOr(p[Keys.TEMP_UNIT], TemperatureUnit.CELSIUS),
        uiDensity = enumOr(p[Keys.DENSITY], UiDensity.NORMAL),
        keepScreenOn = p[Keys.KEEP_SCREEN_ON] ?: true,
        startOnBoot = p[Keys.START_ON_BOOT] ?: false,
        deltaReference = enumOr(p[Keys.DELTA_REFERENCE], DeltaReference.PERSONAL_BEST),
        orientationLocks = ScreenKey.entries.mapNotNull { screen ->
            p[orientationKey(screen)]?.let { value -> enumOrNull<OrientationLock>(value)?.let { screen to it } }
        }.toMap(),
        debugHud = p[Keys.DEBUG_HUD] ?: false,
        drivePreset = enumOr(p[Keys.DRIVE_PRESET], DrivePreset.AUTO),
    )

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E = enumOrNull<E>(name) ?: default

    private inline fun <reified E : Enum<E>> enumOrNull(name: String?): E? =
        name?.let { n -> enumValues<E>().firstOrNull { it.name == n } }

    private fun orientationKey(screen: ScreenKey) = stringPreferencesKey("orientation_${screen.name.lowercase()}")

    private object Keys {
        val PORT = intPreferencesKey("udp_port")
        val SOURCE = stringPreferencesKey("data_source")
        val MOCK_FORMAT = stringPreferencesKey("mock_format")
        val MOCK_SESSION = stringPreferencesKey("mock_session")
        val REPLAY_FILE = stringPreferencesKey("replay_file")
        val THEME = stringPreferencesKey("theme_mode")
        val SPEED_UNIT = stringPreferencesKey("speed_unit")
        val TEMP_UNIT = stringPreferencesKey("temperature_unit")
        val DENSITY = stringPreferencesKey("ui_density")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val START_ON_BOOT = booleanPreferencesKey("start_on_boot")
        val DELTA_REFERENCE = stringPreferencesKey("delta_reference")
        val DEBUG_HUD = booleanPreferencesKey("debug_hud")
        val DRIVE_PRESET = stringPreferencesKey("drive_preset")
    }
}
