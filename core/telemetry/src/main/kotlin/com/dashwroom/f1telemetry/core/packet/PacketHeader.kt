package com.dashwroom.f1telemetry.core.packet

import com.dashwroom.f1telemetry.core.protocol.PacketFormat

/** `struct PacketHeader` — 29 bytes, identical in 2025 and 2026. */
class PacketHeader {
    var packetFormat: Int = 0
    var format: PacketFormat = PacketFormat.F1_25
    var gameYear: Int = 0
    var gameMajorVersion: Int = 0
    var gameMinorVersion: Int = 0
    var packetVersion: Int = 0
    var packetId: Int = 0

    /** uint64 bit pattern. */
    var sessionUid: Long = 0L
    var sessionTime: Float = 0f
    var frameIdentifier: Long = 0L
    var overallFrameIdentifier: Long = 0L
    var playerCarIndex: Int = 0

    /** 255 when there is no split-screen player. */
    var secondaryPlayerCarIndex: Int = 255

    fun copyFrom(other: PacketHeader) {
        packetFormat = other.packetFormat
        format = other.format
        gameYear = other.gameYear
        gameMajorVersion = other.gameMajorVersion
        gameMinorVersion = other.gameMinorVersion
        packetVersion = other.packetVersion
        packetId = other.packetId
        sessionUid = other.sessionUid
        sessionTime = other.sessionTime
        frameIdentifier = other.frameIdentifier
        overallFrameIdentifier = other.overallFrameIdentifier
        playerCarIndex = other.playerCarIndex
        secondaryPlayerCarIndex = other.secondaryPlayerCarIndex
    }
}
