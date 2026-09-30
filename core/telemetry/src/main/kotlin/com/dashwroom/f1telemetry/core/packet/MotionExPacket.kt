package com.dashwroom.f1telemetry.core.packet

import com.dashwroom.f1telemetry.core.protocol.PacketId

/** Packet 13 — extended motion data for the player's car only. Wheel order RL, RR, FL, FR. */
class MotionExPacket : F1Packet {
    override val packetId: Int = PacketId.MOTION_EX
    override val header = PacketHeader()
    val suspensionPosition = FloatArray(4)
    val suspensionVelocity = FloatArray(4)
    val suspensionAcceleration = FloatArray(4)
    val wheelSpeed = FloatArray(4)
    val wheelSlipRatio = FloatArray(4)
    val wheelSlipAngle = FloatArray(4)
    val wheelLatForce = FloatArray(4)
    val wheelLongForce = FloatArray(4)
    var heightOfCogAboveGround = 0f
    var localVelocityX = 0f
    var localVelocityY = 0f
    var localVelocityZ = 0f
    var angularVelocityX = 0f
    var angularVelocityY = 0f
    var angularVelocityZ = 0f
    var angularAccelerationX = 0f
    var angularAccelerationY = 0f
    var angularAccelerationZ = 0f
    var frontWheelsAngle = 0f
    val wheelVertForce = FloatArray(4)
    var frontAeroHeight = 0f
    var rearAeroHeight = 0f
    var frontRollAngle = 0f
    var rearRollAngle = 0f
    var chassisYaw = 0f
    var chassisPitch = 0f
    val wheelCamber = FloatArray(4)
    val wheelCamberGain = FloatArray(4)
}
