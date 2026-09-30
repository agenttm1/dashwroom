package com.dashwroom.f1telemetry.ui.screens.race

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import com.dashwroom.f1telemetry.core.model.SessionState
import com.dashwroom.f1telemetry.core.model.TelemetryStatus
import com.dashwroom.f1telemetry.ui.screens.PlaceholderContent
import com.dashwroom.f1telemetry.ui.screens.PlaceholderScreen
import com.dashwroom.f1telemetry.ui.theme.DashwroomTheme

@Composable
fun RaceScreen(onOpenConnect: () -> Unit) {
    PlaceholderScreen(title = "Race", comingIn = "Phase 2", onOpenConnect = onOpenConnect)
}

@PreviewScreenSizes
@Preview(name = "Tablet", device = Devices.TABLET)
@Composable
private fun RaceScreenPreview() {
    DashwroomTheme {
        PlaceholderContent("Race", "Phase 2", TelemetryStatus(), SessionState.Empty, onOpenConnect = {})
    }
}
