package com.myapp.adventure.step

import android.content.Context
import com.myapp.adventure.service.StepCounterService

/**
 * Wraps [StepCounterService] as a [StepSource].
 *
 * The service already accumulates a delta in [StepCounterService] and writes
 * to [StepHistoryManager]. To avoid double-counting, this source does NOT
 * call recordDelta itself — it only reports what the service has accumulated
 * since the last poll via [consumePendingDelta].
 *
 * Note: StepCounterService writes to StepHistoryManager independently, so this
 * source's delta is informational for the manager (used to compare with HC).
 * The manager applies max-per-bucket semantics when both sources report.
 */
class SensorStepSource : StepSource {

    override val id: String = "sensor"

    override fun isAvailable(context: Context): Boolean {
        return StepCounterService.isSensorAvailable
    }

    override suspend fun getDelta(context: Context): Int {
        // Sensor source does not push deltas through StepSourceManager;
        // StepCounterService already writes them directly.
        // Returning 0 keeps StepSourceManager idempotent.
        return 0
    }
}