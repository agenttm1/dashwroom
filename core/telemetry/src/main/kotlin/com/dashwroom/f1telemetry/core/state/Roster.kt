package com.dashwroom.f1telemetry.core.state

import com.dashwroom.f1telemetry.core.packet.ParticipantsPacket
import com.dashwroom.f1telemetry.core.protocol.PacketFormat
import com.dashwroom.f1telemetry.core.spec.Appendix
import java.text.Normalizer

/** Names and timing codes per vehicle index, refreshed from Participants (only when a name changes). */
internal class Roster {
    val names = Array(PacketFormat.MAX_CARS) { "Car ${it + 1}" }
    val codes = Array(PacketFormat.MAX_CARS) { "C${(it + 1).toString().padStart(2, '0')}" }
    val teamNames = Array(PacketFormat.MAX_CARS) { "" }
    val teamIds = IntArray(PacketFormat.MAX_CARS) { -1 }
    var numActiveCars = 0

    fun update(p: ParticipantsPacket) {
        numActiveCars = p.numActiveCars
        for (i in 0 until p.numCars) {
            val c = p.cars[i]
            val name = c.name.ifBlank { "Car ${i + 1}" }
            if (names[i] != name) {
                names[i] = name
                codes[i] = codeFor(name, c.raceNumber)
            }
            if (teamIds[i] != c.teamId) {
                teamIds[i] = c.teamId
                teamNames[i] = Appendix.teamName(c.teamId)
            }
        }
    }

    fun reset() {
        for (i in 0 until PacketFormat.MAX_CARS) {
            names[i] = "Car ${i + 1}"
            codes[i] = "C${(i + 1).toString().padStart(2, '0')}"
            teamNames[i] = ""
            teamIds[i] = -1
        }
        numActiveCars = 0
    }

    companion object {
        /** "NORRIS" → "NOR", "Lando Norris" → "NOR", "Pérez" → "PER", gamertags → first letters. */
        fun codeFor(name: String, raceNumber: Int): String {
            val last = name.trim().split(' ', '_', '.').lastOrNull { it.isNotBlank() } ?: return "#$raceNumber"
            val plain = Normalizer.normalize(last, Normalizer.Form.NFD).filter { it.isLetter() && it.code < 128 }
            return if (plain.length >= 3) plain.take(3).uppercase() else "#$raceNumber"
        }
    }
}
