package com.dashwroom.f1telemetry.core.packet

import com.dashwroom.f1telemetry.core.protocol.PacketFormat
import com.dashwroom.f1telemetry.core.protocol.PacketId

/** Packet 10 — damage and wear for every car (percentages). Wheel order RL, RR, FL, FR. */
class CarDamagePacket : F1Packet {
    override val packetId: Int = PacketId.CAR_DAMAGE
    override val header = PacketHeader()
    val cars = Array(PacketFormat.MAX_CARS) { CarDamage() }

    class CarDamage {
        val tyresWear = FloatArray(4)
        val tyresDamage = IntArray(4)
        val brakesDamage = IntArray(4)
        val tyreBlisters = IntArray(4)
        var frontLeftWingDamage = 0
        var frontRightWingDamage = 0
        var rearWingDamage = 0
        var floorDamage = 0
        var diffuserDamage = 0
        var sidepodDamage = 0
        var drsFault = false
        var ersFault = false
        var gearBoxDamage = 0
        var engineDamage = 0
        var engineMguhWear = 0
        var engineEsWear = 0
        var engineCeWear = 0
        var engineIceWear = 0
        var engineMgukWear = 0
        var engineTcWear = 0
        var engineBlown = false
        var engineSeized = false
    }
}
