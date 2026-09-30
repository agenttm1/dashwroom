package com.dashwroom.f1telemetry.telemetry

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.dashwroom.f1telemetry.MainActivity
import com.dashwroom.f1telemetry.R
import com.dashwroom.f1telemetry.core.model.ConnectionState
import com.dashwroom.f1telemetry.core.model.SourceKind
import com.dashwroom.f1telemetry.core.model.TelemetryStatus
import kotlin.math.roundToInt

/** What the persistent notification shows; compared for equality so updates only happen on change. */
data class NotificationContent(val title: String, val text: String)

object TelemetryNotification {
    const val CHANNEL_ID = "telemetry"
    const val NOTIFICATION_ID = 20777

    fun content(status: TelemetryStatus, address: String?, port: Int): NotificationContent {
        val state = when (status.connection) {
            ConnectionState.CONNECTED -> "Connected"
            ConnectionState.STALE -> "Stalled"
            ConnectionState.SEARCHING -> "Searching"
        }
        val source = when (status.source) {
            SourceKind.MOCK -> " · mock data"
            SourceKind.REPLAY -> " · replay"
            else -> ""
        }
        val text = when {
            status.sourceError != null -> status.sourceError!!
            status.connection == ConnectionState.SEARCHING -> "Waiting for the game on ${address ?: "Wi-Fi"}:$port"
            else -> "${status.telemetryHz.roundToInt()} Hz · ${status.packetsPerSecond.roundToInt()} packets/s" +
                (status.lossPercent?.let { " · ${"%.1f".format(it)}% loss" } ?: "")
        }
        return NotificationContent("$state$source", text)
    }

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_LOW).apply {
                description = context.getString(R.string.notification_channel_description)
                setShowBadge(false)
            },
        )
    }

    fun build(context: Context, content: NotificationContent) =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(
                PendingIntent.getActivity(
                    context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .addAction(
                0, context.getString(R.string.notification_stop),
                PendingIntent.getService(
                    context, 1, Intent(context, UdpTelemetryService::class.java).setAction(UdpTelemetryService.ACTION_STOP),
                    PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()
}
