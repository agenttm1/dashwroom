package com.dashwroom.f1telemetry.ui.screens.qualifying

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
fun QualifyingScreen(onOpenConnect: () -> Unit) {
    PlaceholderScreen(title = "Qualifying", comingIn = "Phase 3", onOpenConnect = onOpenConnect)
}

@PreviewScreenSizes
@Preview(name = "Tablet", device = Devices.TABLET)
@Composable
private fun QualifyingScreenPreview() {
    DashwroomTheme {
        PlaceholderContent("Qualifying", "Phase 3", TelemetryStatus(), SessionState.Empty, onOpenConnect = {})
    }
}
