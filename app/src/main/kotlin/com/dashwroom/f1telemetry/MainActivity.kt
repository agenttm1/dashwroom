package com.dashwroom.f1telemetry

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.dashwroom.f1telemetry.core.TelemetryRepository
import com.dashwroom.f1telemetry.core.model.SourceKind
import com.dashwroom.f1telemetry.replay.mock.MockSessionMode
import com.dashwroom.f1telemetry.data.settings.SettingsRepository
import com.dashwroom.f1telemetry.telemetry.SourceController
import com.dashwroom.f1telemetry.telemetry.UdpTelemetryService
import com.dashwroom.f1telemetry.ui.DashwroomRoot
import com.dashwroom.f1telemetry.ui.navigation.Destination
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var repository: TelemetryRepository
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var sourceController: SourceController

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        applyLaunchExtras(intent)
        val start = Destination.fromKey(intent.getStringExtra(EXTRA_START_DESTINATION)) ?: Destination.OVERVIEW
        setContent {
            DashwroomRoot(
                activity = this,
                repository = repository,
                settingsRepository = settings,
                startDestination = start,
            )
        }
        if (savedInstanceState == null) requestNotificationPermission()
    }

    override fun onStart() {
        super.onStart()
        UdpTelemetryService.start(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        applyLaunchExtras(intent)
    }

    /**
     * Lets benchmarks (and adb) start straight into a source:
     * `--es source mock [--es session qualifying]`.
     */
    private fun applyLaunchExtras(intent: Intent?) {
        val source = intent?.getStringExtra(EXTRA_SOURCE) ?: return
        val kind = SourceKind.entries.firstOrNull { it.name.equals(source, ignoreCase = true) } ?: return
        val session = MockSessionMode.entries.firstOrNull { it.name.equals(intent.getStringExtra(EXTRA_MOCK_SESSION), ignoreCase = true) }
        sourceController.overrideSource(kind, session)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    companion object {
        const val EXTRA_SOURCE = "source"
        const val EXTRA_MOCK_SESSION = "session"
        const val EXTRA_START_DESTINATION = "start"
    }
}
