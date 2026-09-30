package com.dashwroom.f1telemetry.telemetry

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dashwroom.f1telemetry.data.settings.SettingsRepository
import com.dashwroom.f1telemetry.di.ApplicationScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Starts listening after boot when "Start service on boot" is enabled. */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    @Inject lateinit var settings: SettingsRepository
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        scope.launch {
            try {
                if (settings.current().startOnBoot) UdpTelemetryService.start(context.applicationContext)
            } finally {
                pending.finish()
            }
        }
    }
}
