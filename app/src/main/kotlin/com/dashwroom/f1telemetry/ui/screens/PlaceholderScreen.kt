package com.dashwroom.f1telemetry.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dashwroom.f1telemetry.core.model.SessionState
import com.dashwroom.f1telemetry.core.model.TelemetryStatus
import com.dashwroom.f1telemetry.ui.components.WaitingForTelemetry

/**
 * Shared shell for screens whose content lands in a later phase: shows the "waiting for
 * telemetry" skeleton, or — once data flows — confirms reception and names the session.
 */
@Composable
fun PlaceholderScreen(
    title: String,
    comingIn: String,
    onOpenConnect: () -> Unit,
    viewModel: TelemetryStatusViewModel = hiltViewModel(),
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    PlaceholderContent(title, comingIn, status, session, onOpenConnect)
}

@Composable
fun PlaceholderContent(
    title: String,
    comingIn: String,
    status: TelemetryStatus,
    session: SessionState,
    onOpenConnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!status.isReceiving) {
        WaitingForTelemetry(
            title = "Waiting for telemetry",
            message = "Start a session in F1 25 with UDP telemetry pointed at this phone. $title fills in as soon as packets arrive.",
            actionLabel = "Connection setup",
            onAction = onOpenConnect,
            modifier = modifier,
        )
    } else {
        val info = session.info
        val where = if (info != null) "${info.trackName} · ${info.sessionTypeName}" else "Receiving data"
        WaitingForTelemetry(
            title = where,
            message = "Telemetry is flowing (${session.participants.size} drivers). The $title screen is built in $comingIn.",
            actionLabel = "Diagnostics",
            onAction = onOpenConnect,
            modifier = modifier,
        )
    }
}
