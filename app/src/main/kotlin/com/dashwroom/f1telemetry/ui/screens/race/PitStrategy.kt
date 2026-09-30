package com.dashwroom.f1telemetry.ui.screens.race

import androidx.compose.runtime.Immutable
import com.dashwroom.f1telemetry.core.model.DriverState
import com.dashwroom.f1telemetry.core.model.RaceState
import com.dashwroom.f1telemetry.core.model.SessionInfo

/** What the pit-window / undercut helper shows for the player. Pure data, computed by [PitStrategy]. */
@Immutable
data class PitAdvice(
    val currentLap: Int,
    val idealLap: Int,
    val latestLap: Int,
    val windowState: WindowState,
    /** Pit-lane time loss used for the projection, ms. */
    val pitLossMs: Int,
    val pitLossEstimated: Boolean,
    /** 1.0 normally, lower under safety car / VSC when the field is slowed. */
    val neutralisedFactor: Float,
    val rejoinPosition: Int,
    /** Car the player would rejoin behind, and by how much (ms). */
    val rejoinBehind: DriverState?,
    val rejoinBehindGapMs: Int,
    /** Car that would be just behind the player after the stop, and by how much (ms). */
    val rejoinAhead: DriverState?,
    val rejoinAheadGapMs: Int,
    val carAhead: DriverState?,
    val undercutInRange: Boolean,
    val carBehind: DriverState?,
    val undercutThreat: Boolean,
) {
    enum class WindowState { NOT_SET, BEFORE, OPEN, CLOSING, PASSED }
}

object PitStrategy {
    /** Used until someone has actually pitted this session. */
    const val DEFAULT_PIT_LOSS_MS = 22_000

    /** Rough gain from fresh tyres over an in/out-lap cycle; beyond this an undercut rarely works. */
    const val UNDERCUT_RANGE_MS = 2_500

    fun compute(race: RaceState, info: SessionInfo?): PitAdvice? {
        val player = race.player ?: return null
        if (!player.isRunning) return null
        val observed = race.observedPitLaneTimeMs
        val factor = when (info?.safetyCarStatus) {
            1 -> 0.55f // full safety car: the field is slow, the stop costs roughly half
            2 -> 0.7f // virtual safety car
            else -> 1f
        }
        val loss = ((observed ?: DEFAULT_PIT_LOSS_MS) * factor).toInt()

        val lap = player.currentLap
        val ideal = info?.pitStopWindowIdealLap ?: 0
        val latest = info?.pitStopWindowLatestLap ?: 0
        val windowState = when {
            ideal <= 0 && latest <= 0 -> PitAdvice.WindowState.NOT_SET
            lap < ideal -> PitAdvice.WindowState.BEFORE
            latest > 0 && lap > latest -> PitAdvice.WindowState.PASSED
            latest > 0 && lap >= latest - 1 -> PitAdvice.WindowState.CLOSING
            else -> PitAdvice.WindowState.OPEN
        }

        // Race time of every running car on the player's lap, in ms behind the leader.
        val others = race.drivers.filter { it.isRunning && it.vehicleIndex != player.vehicleIndex && it.lapsBehindLeader == player.lapsBehindLeader }
        val playerAfter = player.gapToLeaderMs + loss
        val ahead = others.filter { it.gapToLeaderMs <= playerAfter }.maxByOrNull { it.gapToLeaderMs }
        val behind = others.filter { it.gapToLeaderMs > playerAfter }.minByOrNull { it.gapToLeaderMs }
        val lapped = race.drivers.count { it.isRunning && it.vehicleIndex != player.vehicleIndex && it.lapsBehindLeader < player.lapsBehindLeader }
        val rejoin = lapped + others.count { it.gapToLeaderMs <= playerAfter } + 1

        val carAhead = race.drivers.firstOrNull { it.isRunning && it.position == player.position - 1 }
        val carBehind = race.drivers.firstOrNull { it.isRunning && it.position == player.position + 1 }
        return PitAdvice(
            currentLap = lap,
            idealLap = ideal,
            latestLap = latest,
            windowState = windowState,
            pitLossMs = loss,
            pitLossEstimated = observed == null,
            neutralisedFactor = factor,
            rejoinPosition = rejoin,
            rejoinBehind = ahead,
            rejoinBehindGapMs = if (ahead != null) playerAfter - ahead.gapToLeaderMs else 0,
            rejoinAhead = behind,
            rejoinAheadGapMs = if (behind != null) behind.gapToLeaderMs - playerAfter else 0,
            carAhead = carAhead,
            undercutInRange = carAhead != null && player.intervalMs in 1..UNDERCUT_RANGE_MS,
            carBehind = carBehind,
            // A car close behind on tyres at least as old as ours can take the undercut on us.
            undercutThreat = carBehind != null && carBehind.intervalMs in 1..UNDERCUT_RANGE_MS && carBehind.tyreAgeLaps >= player.tyreAgeLaps - 2,
        )
    }
}
