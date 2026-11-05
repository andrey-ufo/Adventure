package com.myapp.adventure.step

import android.content.Context

/**
 * Abstraction over a step data source.
 *
 * Implementations must be cheap to construct. Heavy initialization belongs
 * in [StepSourceManager].
 */
interface StepSource {

    /** Stable identifier used for logging and prefs. */
    val id: String

    /**
     * Whether this source can currently be used.
     * For Health Connect this checks [HealthConnectClient.getSdkStatus] and
     * whether the READ_STEPS permission has been granted.
     */
    fun isAvailable(context: Context): Boolean

    /**
     * Delta (steps) since the previous call. Return 0 if nothing new.
     *
     * Implementations must handle their own baseline internally. For the sensor
     * source, delta is derived from [StepCounterService] events. For Health
     * Connect, delta is derived from aggregate calls over a sliding window.
     */
    suspend fun getDelta(context: Context): Int
}