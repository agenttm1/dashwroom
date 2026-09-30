package com.dashwroom.f1telemetry.core.model

import com.dashwroom.f1telemetry.core.protocol.PacketFormat
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** Connection + diagnostics snapshot, published a few times per second. Immutable. */
data class TelemetryStatus(
    val connection: ConnectionState = ConnectionState.SEARCHING,
    val source: SourceKind? = null,
    val sourceDescription: String = "",
    val sourceError: String? = null,
    val packetsPerSecond: Float = 0f,
    /** Car Telemetry packets per second — equals the in-game "UDP Send Rate" when healthy. */
    val telemetryHz: Float = 0f,
    val bytesPerSecond: Float = 0f,
    /** Estimated UDP loss over the last few seconds; null until there is enough data. */
    val lossPercent: Float? = null,
    val game: GameInfo? = null,
    val sessionUid: Long? = null,
    val sender: String? = null,
    val totalPackets: Long = 0,
    val msSinceLastPacket: Long? = null,
    val perType: ImmutableList<PacketTypeStats> = persistentListOf(),
    val unknownFormatPackets: Long = 0,
    val lastUnknownFormat: Int? = null,
    val sizeMismatches: Long = 0,
    val coalescedPackets: Long = 0,
    val outOfOrderPackets: Long = 0,
    val tooShortPackets: Long = 0,
) {
    val isReceiving: Boolean get() = connection != ConnectionState.SEARCHING
}

data class GameInfo(
    val packetFormat: Int,
    val format: PacketFormat?,
    val gameYear: Int,
    val majorVersion: Int,
    val minorVersion: Int,
) {
    val versionLabel: String get() = "v$majorVersion.${minorVersion.toString().padStart(2, '0')}"
    val displayName: String get() = format?.displayName ?: "Unsupported format $packetFormat"
}

data class PacketTypeStats(
    val packetId: Int,
    val name: String,
    val count: Long,
    val perSecond: Float,
    val sizeMismatches: Long,
)
