package com.dashwroom.f1telemetry.core.model

enum class ConnectionState {
    /** Nothing received for [SEARCHING_AFTER_MS] (or ever). */
    SEARCHING,

    /** Packets flowing. */
    CONNECTED,

    /** Had data, but nothing for more than [STALE_AFTER_MS] — game paused, menus, or Wi-Fi hiccup. */
    STALE;

    companion object {
        const val STALE_AFTER_MS = 1_000L
        const val SEARCHING_AFTER_MS = 3_000L

        fun fromAge(msSinceLastPacket: Long?): ConnectionState = when {
            msSinceLastPacket == null || msSinceLastPacket > SEARCHING_AFTER_MS -> SEARCHING
            msSinceLastPacket > STALE_AFTER_MS -> STALE
            else -> CONNECTED
        }
    }
}

enum class SourceKind { LIVE, MOCK, REPLAY }
