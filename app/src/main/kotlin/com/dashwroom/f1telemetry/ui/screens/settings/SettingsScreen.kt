package com.dashwroom.f1telemetry.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dashwroom.f1telemetry.BuildConfig
import com.dashwroom.f1telemetry.core.model.SourceKind
import com.dashwroom.f1telemetry.core.protocol.PacketFormat
import com.dashwroom.f1telemetry.replay.mock.MockSessionMode
import com.dashwroom.f1telemetry.data.recording.RecordingFile
import com.dashwroom.f1telemetry.data.settings.AppSettings
import com.dashwroom.f1telemetry.data.settings.DeltaReference
import com.dashwroom.f1telemetry.data.settings.OrientationLock
import com.dashwroom.f1telemetry.data.settings.ScreenKey
import com.dashwroom.f1telemetry.data.settings.SpeedUnit
import com.dashwroom.f1telemetry.data.settings.TemperatureUnit
import com.dashwroom.f1telemetry.data.settings.ThemeMode
import com.dashwroom.f1telemetry.data.settings.UiDensity
import com.dashwroom.f1telemetry.ui.components.DashCard
import com.dashwroom.f1telemetry.ui.theme.DashwroomTheme
import java.text.DateFormat
import java.util.Date

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (state.loaded) SettingsContent(state, viewModel)
}

@Composable
internal fun SettingsContent(state: SettingsUiState, vm: SettingsViewModel?) {
    val s = state.settings
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Settings read best as one comfortable column, even on tablets.
        Column(Modifier.widthIn(max = 720.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DashCard(title = "Telemetry source") {
                Choice("Data source", SourceKind.entries, s.dataSource, { it.name.lowercase().replaceFirstChar(Char::uppercase) }) { vm?.setSource(it) }
                if (s.dataSource == SourceKind.MOCK) {
                    Choice("Mock packet format", PacketFormat.entries, s.mockFormat, { it.wireValue.toString() }) { vm?.setMockFormat(it) }
                    Choice("Mock session", MockSessionMode.entries, s.mockSession, { it.label }) { vm?.setMockSession(it) }
                }
                if (s.dataSource == SourceKind.REPLAY) RecordingPicker(state.recordings, s.replayFile, vm)
                PortField(s.udpPort) { vm?.setPort(it) }
                Toggle("Start listening on boot", "Begin receiving automatically after the phone restarts.", s.startOnBoot) { vm?.setStartOnBoot(it) }
            }
            DashCard(title = "Display") {
                Choice("Theme", ThemeMode.entries, s.themeMode, { it.name.lowercase().replaceFirstChar(Char::uppercase) }) { vm?.setTheme(it) }
                Choice("Size", UiDensity.entries, s.uiDensity, { it.label }) { vm?.setDensity(it) }
                Choice("Speed", SpeedUnit.entries, s.speedUnit, { it.label }) { vm?.setSpeedUnit(it) }
                Choice("Temperature", TemperatureUnit.entries, s.temperatureUnit, { it.label }) { vm?.setTemperatureUnit(it) }
                Choice("Delta reference lap", DeltaReference.entries, s.deltaReference, { it.label }) { vm?.setDeltaReference(it) }
                Toggle("Keep screen on", "While telemetry is being received.", s.keepScreenOn) { vm?.setKeepScreenOn(it) }
            }
            DashCard(title = "Orientation lock") {
                Text(
                    "Per screen. On large screens Android 16+ may ignore locks by design.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ScreenKey.entries.forEach { screen ->
                    Choice(screen.label, OrientationLock.entries, s.orientationFor(screen), { it.label }) { vm?.setOrientation(screen, it) }
                }
            }
            DashCard(title = "Debug") {
                Toggle(
                    "Performance HUD",
                    "Overlay with packets/s, frames/s, receive→draw latency, janky frames and allocation rate.",
                    s.debugHud,
                ) { vm?.setDebugHud(it) }
                Text(
                    "Dashwroom ${BuildConfig.VERSION_NAME} (${BuildConfig.BUILD_TYPE})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun <T> Choice(label: String, options: List<T>, selected: T, name: (T) -> String, onSelect: (T) -> Unit) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(6.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, option ->
                SegmentedButton(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    shape = SegmentedButtonDefaults.itemShape(i, options.size),
                ) { Text(name(option), maxLines = 1) }
            }
        }
    }
}

@Composable
private fun Toggle(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp)
            .selectable(selected = checked, role = Role.Switch, onClick = { onChange(!checked) }),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun PortField(port: Int, onCommit: (Int) -> Unit) {
    var text by rememberSaveable(port) { mutableStateOf(port.toString()) }
    val parsed = text.toIntOrNull()
    val valid = parsed != null && parsed in 1024..65_535
    OutlinedTextField(
        value = text,
        onValueChange = { v ->
            text = v.filter(Char::isDigit).take(5)
            text.toIntOrNull()?.takeIf { it in 1024..65_535 }?.let(onCommit)
        },
        label = { Text("UDP port") },
        supportingText = { Text(if (valid) "Must match the game's UDP Port (default 20777)." else "Enter a port between 1024 and 65535.") },
        isError = !valid,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
}

@Composable
private fun RecordingPicker(recordings: List<RecordingFile>, selected: String?, vm: SettingsViewModel?) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Text("Recording to replay", style = MaterialTheme.typography.bodyMedium)
        if (recordings.isEmpty()) {
            Text(
                "No recordings yet. Use Record session on the Connect screen while connected to the game.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        RecordingRow("Most recent", null, selected == null) { vm?.setReplayFile(null) }
        recordings.forEach { r ->
            val detail = "${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(r.modifiedMillis))} · ${r.sizeBytes / 1024} kB"
            RecordingRow(r.name, detail, selected == r.path, onDelete = { vm?.deleteRecording(r.path) }) { vm?.setReplayFile(r.path) }
        }
    }
}

@Composable
private fun RecordingRow(title: String, detail: String?, selected: Boolean, onDelete: (() -> Unit)? = null, onSelect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = onSelect).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (onDelete != null) IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, contentDescription = "Delete $title") }
    }
}

@PreviewScreenSizes
@Preview(name = "Tablet", device = Devices.TABLET)
@Composable
private fun SettingsPreview() {
    DashwroomTheme { SettingsContent(SettingsUiState(AppSettings(dataSource = SourceKind.MOCK), loaded = true), vm = null) }
}
