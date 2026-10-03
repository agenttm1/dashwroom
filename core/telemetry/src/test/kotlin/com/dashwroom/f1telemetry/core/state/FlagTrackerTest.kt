package com.dashwroom.f1telemetry.core.state

import com.dashwroom.f1telemetry.core.model.FiaFlag
import com.dashwroom.f1telemetry.core.model.SafetyCarMode
import com.dashwroom.f1telemetry.core.packet.EventCode
import com.dashwroom.f1telemetry.core.packet.EventPacket
import com.dashwroom.f1telemetry.core.packet.SessionPacket
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FlagTrackerTest {
    private val t = FlagTracker()

    private fun event(code: Int, time: Float = 100f, block: EventPacket.() -> Unit = {}) =
        EventPacket().apply { this.code = code; header.sessionTime = time; block() }

    private fun session(sc: Int, yellowZones: Int = 0) = SessionPacket().apply {
        safetyCarStatus = sc
        numMarshalZones = 10
        for (z in 0 until 10) marshalZoneFlag[z] = if (z < yellowZones) 3 else 0
    }

    @Test
    fun `player flag maps and a cleared yellow flashes green once`() {
        t.onPlayerFlag(0, 1f)
        assertThat(t.state.value.playerFlag).isEqualTo(FiaFlag.NONE)
        t.onPlayerFlag(3, 2f)
        assertThat(t.state.value.playerFlag).isEqualTo(FiaFlag.YELLOW)
        assertThat(t.state.value.greenCount).isEqualTo(0)
        t.onPlayerFlag(1, 3f)
        assertThat(t.state.value.playerFlag).isEqualTo(FiaFlag.GREEN)
        assertThat(t.state.value.greenCount).isEqualTo(1)
        // Repeating the same raw value publishes nothing new.
        val before = t.state.value
        t.onPlayerFlag(1, 4f)
        assertThat(t.state.value).isSameInstanceAs(before)
        t.onPlayerFlag(2, 5f)
        assertThat(t.state.value.playerFlag).isEqualTo(FiaFlag.BLUE)
        assertThat(t.state.value.greenCount).isEqualTo(1) // blue → no green flash
    }

    @Test
    fun `safety car follows session status and SCAR phases`() {
        t.onEvent(event(EventCode.SAFETY_CAR) { safetyCarType = 1; safetyCarEventType = 0 })
        assertThat(t.state.value.safetyCar).isEqualTo(SafetyCarMode.FULL)
        t.onSession(session(sc = 1, yellowZones = 3))
        assertThat(t.state.value.yellowZones).isEqualTo(3)
        assertThat(t.state.value.neutralised).isTrue()
        t.onEvent(event(EventCode.SAFETY_CAR) { safetyCarType = 1; safetyCarEventType = 1 })
        assertThat(t.state.value.safetyCarEnding).isTrue()
        t.onSession(session(sc = 1, yellowZones = 2)) // still out, ending flag survives
        assertThat(t.state.value.safetyCarEnding).isTrue()
        t.onEvent(event(EventCode.SAFETY_CAR) { safetyCarType = 1; safetyCarEventType = 3 })
        assertThat(t.state.value.safetyCar).isEqualTo(SafetyCarMode.NONE)
        assertThat(t.state.value.greenCount).isEqualTo(1)
        t.onSession(session(sc = 2))
        assertThat(t.state.value.safetyCar).isEqualTo(SafetyCarMode.VIRTUAL)
        assertThat(t.state.value.safetyCarEnding).isFalse()
    }

    @Test
    fun `red flag holds until the restart`() {
        t.onEvent(event(EventCode.RED_FLAG, time = 50f))
        assertThat(t.state.value.redFlag).isTrue()
        t.onPlayerFlag(3, 60f)
        assertThat(t.state.value.redFlag).isTrue()
        t.onEvent(event(EventCode.LIGHTS_OUT, time = 400f))
        assertThat(t.state.value.redFlag).isFalse()
        assertThat(t.state.value.greenCount).isEqualTo(1)
    }

    @Test
    fun `red flag times out if the game never signals a restart`() {
        t.onEvent(event(EventCode.RED_FLAG, time = 10f))
        t.onPlayerFlag(0, 300f)
        assertThat(t.state.value.redFlag).isTrue()
        t.onPlayerFlag(0, 700f)
        assertThat(t.state.value.redFlag).isFalse()
    }

    @Test
    fun `reset clears everything`() {
        t.onEvent(event(EventCode.RED_FLAG))
        t.onPlayerFlag(3, 1f)
        t.reset()
        assertThat(t.state.value.redFlag).isFalse()
        assertThat(t.state.value.playerFlag).isEqualTo(FiaFlag.NONE)
    }
}
