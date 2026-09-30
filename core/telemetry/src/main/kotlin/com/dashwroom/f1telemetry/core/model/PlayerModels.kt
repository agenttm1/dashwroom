package com.dashwroom.f1telemetry.core.model

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** The player's car in detail (fuel, energy, tyres, brakes, damage), rebuilt at ≤10 Hz. */
data class PlayerCarState(
    val vehicleIndex: Int = 0,
    val fuelInTankKg: Float = 0f,
    val fuelCapacityKg: Float = 0f,
    /** Surplus (+) or deficit (−) in laps versus the race distance — the MFD value. */
    val fuelRemainingLaps: Float = 0f,
    /** 0 lean, 1 standard, 2 rich, 3 max. */
    val fuelMix: Int = 1,
    val ersStoreJ: Float = 0f,
    val ersDeployMode: Int = 0,
    val ersHarvestedThisLapJ: Float = 0f,
    val ersDeployedThisLapJ: Float = 0f,
    /** 2026 only. */
    val ersHarvestLimitJ: Float? = null,
    val tyreVisual: Int = 0,
    val tyreActual: Int = 0,
    val tyreAgeLaps: Int = 0,
    /** Order: rear-left, rear-right, front-left, front-right (the spec's wheel order). */
    val wheels: ImmutableList<WheelState> = persistentListOf(),
    val engineTemperatureC: Int = 0,
    val frontBrakeBias: Int = 0,
    val tractionControl: Int = 0,
    val antiLockBrakes: Boolean = false,
    val pitLimiterOn: Boolean = false,
    val damage: DamageState = DamageState(),
    val tyreSets: ImmutableList<TyreSetState> = persistentListOf(),
    val fittedTyreSetIndex: Int = -1,
    val chassis: ChassisState? = null,
    val hasTelemetry: Boolean = false,
    val hasDamage: Boolean = false,
) {
    companion object {
        const val ERS_MAX_J = 4_000_000f
        const val REAR_LEFT = 0
        const val REAR_RIGHT = 1
        const val FRONT_LEFT = 2
        const val FRONT_RIGHT = 3
    }
}

data class WheelState(
    val surfaceTempC: Int,
    /** Carcass / inner temperature. */
    val innerTempC: Int,
    val pressurePsi: Float,
    val brakeTempC: Int,
    val wearPercent: Float,
    val damagePercent: Int,
    val blistersPercent: Int,
    val brakeDamagePercent: Int,
    val surfaceType: Int,
)

data class DamageState(
    val frontLeftWing: Int = 0,
    val frontRightWing: Int = 0,
    val rearWing: Int = 0,
    val floor: Int = 0,
    val diffuser: Int = 0,
    val sidepod: Int = 0,
    val gearbox: Int = 0,
    val engine: Int = 0,
    val drsFault: Boolean = false,
    val ersFault: Boolean = false,
    val mguhWear: Int = 0,
    val esWear: Int = 0,
    val ceWear: Int = 0,
    val iceWear: Int = 0,
    val mgukWear: Int = 0,
    val tcWear: Int = 0,
    val engineBlown: Boolean = false,
    val engineSeized: Boolean = false,
) {
    val worstBodywork: Int get() = maxOf(frontLeftWing, frontRightWing, rearWing, floor, diffuser, sidepod)
}

data class TyreSetState(
    val index: Int,
    val actualCompound: Int,
    val visualCompound: Int,
    val wearPercent: Int,
    val available: Boolean,
    val recommendedSession: Int,
    val lifeSpanLaps: Int,
    val usableLifeLaps: Int,
    val lapDeltaTimeMs: Int,
    val fitted: Boolean,
)

/** A few Motion Ex values worth showing (player car only). */
data class ChassisState(
    val frontAeroHeightMm: Float,
    val rearAeroHeightMm: Float,
    /** RL, RR, FL, FR. */
    val wheelSlipRatio: ImmutableList<Float>,
    val wheelSlipAngleDeg: ImmutableList<Float>,
    val suspensionPositionMm: ImmutableList<Float>,
)
