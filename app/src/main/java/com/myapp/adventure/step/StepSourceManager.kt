package com.myapp.adventure.step

import android.content.Context
import com.myapp.adventure.utils.Logger
import com.myapp.adventure.utils.StepHistoryManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Chooses the active step source and polls it periodically.
 *
 * Rules:
 *  - HC enabled + installed + permission granted → HC is the active source.
 *    Sensor deltas are suppressed by StepCounterService to avoid double-count.
 *  - Otherwise → sensor is the active source (StepCounterService writes deltas
 *    directly). The poll loop is a no-op in this mode.
 *
 * [isHealthConnectActuallyUsable] is the single point of truth used by both
 * this class and [com.myapp.adventure.service.StepCounterService].
 */
object StepSourceManager {

    private const val TAG = "StepSourceManager"
    private const val PREFS_NAME = "step_source_prefs"
    private const val KEY_USE_HEALTH_CONNECT = "use_health_connect"
    private const val POLL_INTERVAL_MS = 60_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollJob: Job? = null

    private val sensorSource = SensorStepSource()
    private val healthConnectSource = HealthConnectStepSource()

    /**
     * Last observed "HC actually usable" state (enabled + available + permission).
     * Starts false; updated on each pollOnce. Sensor suppresses its own deltas
     * while this is true.
     */
    @Volatile
    private var hcUsable: Boolean = false

    /** For "state changed" logging only; suppresses per-poll noise. */
    private var lastHcState: String = "unknown"

    // ----- User preference -----

    fun isHealthConnectEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_USE_HEALTH_CONNECT, false)
    }

    fun setHealthConnectEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_USE_HEALTH_CONNECT, enabled).apply()
        Logger.log(context, "$TAG: Health Connect enabled=$enabled")
        hcUsable = false
        restartPolling(context.applicationContext)
    }

    /**
     * True iff HC is enabled by the user, available on the device, and its
     * read permission was confirmed granted on a recent poll.
     *
     * This is what StepCounterService checks before recording its own deltas.
     */
    fun isHealthConnectActuallyUsable(context: Context): Boolean {
        if (!isHealthConnectEnabled(context)) return false
        if (!healthConnectSource.isAvailable(context)) return false
        return hcUsable
    }

    /** Kept for SettingsScreen; identical to [isHealthConnectActuallyUsable]. */
    fun isHealthConnectActive(context: Context): Boolean =
        isHealthConnectActuallyUsable(context)

    // ----- Lifecycle -----

    fun start(context: Context) {
        restartPolling(context.applicationContext)
    }

    fun stop() {
        pollJob?.cancel()
        pollJob = null
        hcUsable = false
    }

    private fun restartPolling(appContext: Context) {
        stop()
        pollJob = scope.launch {
            while (isActive) {
                try {
                    pollOnce(appContext)
                } catch (e: Exception) {
                    Logger.log(appContext, "$TAG: poll failed: ${e.message}")
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private suspend fun pollOnce(appContext: Context) {
        val enabled = isHealthConnectEnabled(appContext)
        val available = enabled && healthConnectSource.isAvailable(appContext)
        val permitted = available && healthConnectSource.hasReadPermission(appContext)

        val newState = when {
            !enabled -> "disabled"
            !available -> "unavailable"
            !permitted -> "permission_missing"
            else -> "active"
        }
        if (newState != lastHcState) {
            Logger.log(appContext, "$TAG: HC state=$newState")
            lastHcState = newState
        }
        hcUsable = (newState == "active")
        if (!hcUsable) return

        val delta = healthConnectSource.getDelta(appContext)
        if (delta > 0) {
            StepHistoryManager.applyHealthConnectDelta(appContext, delta)
        }
    }
}