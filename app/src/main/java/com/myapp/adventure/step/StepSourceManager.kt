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
 * - If user preference is "health_connect" AND HC is available AND permission granted
 *   → polls HealthConnectStepSource.
 * - Otherwise → SensorStepSource (passive; StepCounterService already writes deltas).
 *
 * Polling interval is 60 seconds. Health Connect may lag, so the gate in
 * AdventureReaderFragment updates with a delay when HC is active. This is documented in UI.
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

    // ----- User preference -----

    fun isHealthConnectEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_USE_HEALTH_CONNECT, false)
    }

    fun setHealthConnectEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_USE_HEALTH_CONNECT, enabled).apply()
        Logger.log(context, "$TAG: Health Connect enabled=$enabled")
        restartPolling(context.applicationContext)
    }

    /**
     * True if the user enabled HC AND HC is currently usable (installed + permission granted).
     * UI uses this to decide whether to show "updating every N minutes" hint.
     */
    fun isHealthConnectActive(context: Context): Boolean {
        if (!isHealthConnectEnabled(context)) return false
        if (!healthConnectSource.isAvailable(context)) return false
        // Permission check is suspend; we use a lightweight synchronous heuristic here.
        // The actual check happens on each poll; if permission is missing, getDelta returns 0
        // and the source falls back to sensor on the next poll.
        return true
    }

    // ----- Lifecycle -----

    /** Call from SetupFragment / MainActivity once per process. */
    fun start(context: Context) {
        restartPolling(context.applicationContext)
    }

    fun stop() {
        pollJob?.cancel()
        pollJob = null
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
        val useHc = isHealthConnectEnabled(appContext) &&
                healthConnectSource.isAvailable(appContext) &&
                healthConnectSource.hasReadPermission(appContext)

        if (!useHc) {
            // Sensor source writes deltas directly via StepCounterService.
            // Nothing to push here.
            return
        }

        val delta = healthConnectSource.getDelta(appContext)
        if (delta > 0) {
            // Use applyBucketMax through a public wrapper (see StepHistoryManager patch below).
            StepHistoryManager.applyHealthConnectDelta(appContext, delta)
            Logger.log(appContext, "$TAG: HC delta=$delta applied")
        }
    }
}