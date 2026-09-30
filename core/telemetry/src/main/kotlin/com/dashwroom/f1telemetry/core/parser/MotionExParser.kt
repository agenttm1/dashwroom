package com.dashwroom.f1telemetry.core.parser

import com.dashwroom.f1telemetry.core.packet.MotionExPacket
import com.dashwroom.f1telemetry.core.protocol.f32
import java.nio.ByteBuffer

/**
 * Packet 13 — `PacketMotionExData`, 273 bytes in both formats, all floats from @29:
 * ```
 *  29 m_suspensionPosition[4]      45 m_suspensionVelocity[4]     61 m_suspensionAcceleration[4]
 *  77 m_wheelSpeed[4]              93 m_wheelSlipRatio[4]        109 m_wheelSlipAngle[4]
 * 125 m_wheelLatForce[4]          141 m_wheelLongForce[4]        157 m_heightOfCOGAboveGround
 * 161 m_localVelocityX  165 Y  169 Z            173 m_angularVelocityX  177 Y  181 Z
 * 185 m_angularAccelerationX  189 Y  193 Z      197 m_frontWheelsAngle
 * 201 m_wheelVertForce[4]         217 m_frontAeroHeight          221 m_rearAeroHeight
 * 225 m_frontRollAngle            229 m_rearRollAngle            233 m_chassisYaw
 * 237 m_chassisPitch              241 m_wheelCamber[4]           257 m_wheelCamberGain[4]
 * ```
 */
internal object MotionExParser {
    fun parse(buf: ByteBuffer, out: MotionExPacket) {
        for (w in 0 until 4) {
            out.suspensionPosition[w] = buf.f32(29 + w * 4)
            out.suspensionVelocity[w] = buf.f32(45 + w * 4)
            out.suspensionAcceleration[w] = buf.f32(61 + w * 4)
            out.wheelSpeed[w] = buf.f32(77 + w * 4)
            out.wheelSlipRatio[w] = buf.f32(93 + w * 4)
            out.wheelSlipAngle[w] = buf.f32(109 + w * 4)
            out.wheelLatForce[w] = buf.f32(125 + w * 4)
            out.wheelLongForce[w] = buf.f32(141 + w * 4)
            out.wheelVertForce[w] = buf.f32(201 + w * 4)
            out.wheelCamber[w] = buf.f32(241 + w * 4)
            out.wheelCamberGain[w] = buf.f32(257 + w * 4)
        }
        out.heightOfCogAboveGround = buf.f32(157)
        out.localVelocityX = buf.f32(161)
        out.localVelocityY = buf.f32(165)
        out.localVelocityZ = buf.f32(169)
        out.angularVelocityX = buf.f32(173)
        out.angularVelocityY = buf.f32(177)
        out.angularVelocityZ = buf.f32(181)
        out.angularAccelerationX = buf.f32(185)
        out.angularAccelerationY = buf.f32(189)
        out.angularAccelerationZ = buf.f32(193)
        out.frontWheelsAngle = buf.f32(197)
        out.frontAeroHeight = buf.f32(217)
        out.rearAeroHeight = buf.f32(221)
        out.frontRollAngle = buf.f32(225)
        out.rearRollAngle = buf.f32(229)
        out.chassisYaw = buf.f32(233)
        out.chassisPitch = buf.f32(237)
    }
}
