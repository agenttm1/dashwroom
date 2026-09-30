package com.dashwroom.f1telemetry.replay.mock

import com.dashwroom.f1telemetry.core.packet.EventPacket

/**
 * Fixed-size ring of pending events. Slots are pre-allocated [EventPacket]s, so raising an event
 * during the simulation step never allocates. Overflow drops the oldest event.
 */
class MockEvents(capacity: Int = 64) {
    private val slots = Array(capacity) { EventPacket() }
    private var head = 0
    private var size = 0

    val isEmpty: Boolean get() = size == 0

    /** Claims the next slot, pre-filled with [code]; the caller sets the detail fields. */
    fun raise(code: Int): EventPacket {
        if (size == slots.size) {
            head = (head + 1) % slots.size
            size--
        }
        val slot = slots[(head + size) % slots.size]
        size++
        slot.code = code
        slot.vehicleIdx = 255
        slot.reason = 0
        return slot
    }

    /** Removes and returns the oldest event; valid until the next [raise]. */
    fun poll(): EventPacket? {
        if (size == 0) return null
        val slot = slots[head]
        head = (head + 1) % slots.size
        size--
        return slot
    }

    fun clear() {
        head = 0
        size = 0
    }
}
