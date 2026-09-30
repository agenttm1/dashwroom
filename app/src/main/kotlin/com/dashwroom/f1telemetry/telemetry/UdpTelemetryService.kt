package com.dashwroom.f1telemetry.telemetry

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.dashwroom.f1telemetry.core.TelemetryRepository
import com.dashwroom.f1telemetry.core.model.SourceKind
import com.dashwroom.f1telemetry.data.network.NetworkMonitor
import com.dashwroom.f1telemetry.data.settings.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service that keeps telemetry flowing with the screen off or the app in the
 * background. Holds a low-latency Wi-Fi lock (live source only) and a partial wake lock, and
 * shows connection state + packet rate in its notification.
 */
@AndroidEntryPoint
class UdpTelemetryService : LifecycleService() {
    @Inject lateinit var repository: TelemetryRepository
    @Inject lateinit var sourceController: SourceController
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var network: NetworkMonitor

    private var wifiLock: WifiManager.WifiLock? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastContent: NotificationContent? = null

    override fun onCreate() {
        super.onCreate()
        TelemetryNotification.ensureChannel(this)
        val initial = NotificationContent("Starting", "Opening the telemetry port…")
        ServiceCompat.startForeground(
            this, TelemetryNotification.NOTIFICATION_ID, TelemetryNotification.build(this, initial),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0,
        )
        sourceController.start()
        lifecycleScope.launch {
            // Timed wake lock, renewed while the service runs, so the OS can always reclaim it.
            while (true) {
                acquireWakeLock()
                delay(WAKE_LOCK_RENEW_MS)
            }
        }

        lifecycleScope.launch {
            settings.settings.map { it.dataSource }.distinctUntilChanged().collect { source ->
                if (source == SourceKind.LIVE) acquireWifiLock() else releaseWifiLock()
            }
        }
        lifecycleScope.launch {
            combine(repository.status, network.info, settings.settings) { status, net, s ->
                TelemetryNotification.content(status, net.primaryAddress, s.udpPort)
            }.distinctUntilChanged().conflate().collect { content ->
                if (content != lastContent) {
                    lastContent = content
                    postNotification(content)
                }
                delay(NOTIFICATION_MIN_INTERVAL_MS) // at most one update per second
            }
        }
    }

    private fun postNotification(content: NotificationContent) {
        val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!allowed) return // the service keeps running; only its status text is hidden
        NotificationManagerCompat.from(this).notify(TelemetryNotification.NOTIFICATION_ID, TelemetryNotification.build(this, content))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        sourceController.stop()
        releaseWifiLock()
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        super.onDestroy()
    }

    private fun acquireWakeLock() {
        val lock = wakeLock ?: getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Dashwroom:telemetry")
            .apply { setReferenceCounted(false) }
            .also { wakeLock = it }
        lock.acquire(WAKE_LOCK_TIMEOUT_MS)
    }

    @Suppress("DEPRECATION")
    private fun acquireWifiLock() {
        if (wifiLock?.isHeld == true) return
        val wm = applicationContext.getSystemService(WifiManager::class.java) ?: return
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) WifiManager.WIFI_MODE_FULL_LOW_LATENCY
        else WifiManager.WIFI_MODE_FULL_HIGH_PERF
        wifiLock = wm.createWifiLock(mode, "Dashwroom:udp").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseWifiLock() {
        wifiLock?.takeIf { it.isHeld }?.release()
        wifiLock = null
    }

    companion object {
        const val ACTION_STOP = "com.dashwroom.f1telemetry.action.STOP"
        private const val NOTIFICATION_MIN_INTERVAL_MS = 1_000L
        private const val WAKE_LOCK_TIMEOUT_MS = 60 * 60 * 1000L
        private const val WAKE_LOCK_RENEW_MS = 30 * 60 * 1000L

        /** Safe to call repeatedly; failures (background-start limits) are swallowed. */
        fun start(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, UdpTelemetryService::class.java)) }
        }
    }
}
