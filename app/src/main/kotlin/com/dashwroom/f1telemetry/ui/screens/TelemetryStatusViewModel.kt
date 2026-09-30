package com.dashwroom.f1telemetry.ui.screens

import androidx.lifecycle.ViewModel
import com.dashwroom.f1telemetry.core.TelemetryRepository
import com.dashwroom.f1telemetry.core.model.SessionState
import com.dashwroom.f1telemetry.core.model.TelemetryStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Connection status + session model for screens that only need to know whether data is flowing. */
@HiltViewModel
class TelemetryStatusViewModel @Inject constructor(repository: TelemetryRepository) : ViewModel() {
    val status: StateFlow<TelemetryStatus> = repository.status
    val session: StateFlow<SessionState> = repository.session
}
