package com.myapp.adventure.step

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.myapp.adventure.utils.Logger
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Health Connect step source.
 *
 * Reads aggregate step count for "today" from Health Connect. The first call
 * establishes a baseline (today's total at that moment). Each subsequent call
 * returns the difference between today's total and the baseline.
 *
 * Data may arrive with a delay: Health Connect aggregates records written by
 * other apps, and Google recommends not writing more than once every 15 min.
 * So the game gate may update with a lag — we document this in UI.
 */
class HealthConnectStepSource : StepSource {

    companion object {
        private const val TAG = "HealthConnectStepSource"
        const val PROVIDER_PACKAGE = "com.google.android.apps.healthdata"
    }

    override val id: String = "health_connect"

    // Baseline: today's aggregate at the moment we started using HC for this day.
    @Volatile private var baselineDate: String? = null
    @Volatile private var baselineTotal: Long = 0L
    @Volatile private var lastSeenTotal: Long = 0L

    override fun isAvailable(context: Context): Boolean {
        val status = HealthConnectClient.getSdkStatus(context, PROVIDER_PACKAGE)
        if (status != HealthConnectClient.SDK_AVAILABLE) return false
        return true
    }

    suspend fun hasReadPermission(context: Context): Boolean {
        return try {
            val client = HealthConnectClient.getOrCreate(context)
            val granted = client.permissionController.getGrantedPermissions()
            granted.contains(HealthPermission.getReadPermission(StepsRecord::class))
        } catch (e: Exception) {
            Logger.log(context, "$TAG: permission check failed: ${e.message}")
            false
        }
    }

    override suspend fun getDelta(context: Context): Int {
        if (!hasReadPermission(context)) return 0

        val today = LocalDate.now()
        val todayKey = today.toString()

        return try {
            val client = HealthConnectClient.getOrCreate(context)
            val zone = ZoneId.systemDefault()
            val start = today.atStartOfDay(zone).toInstant()
            val end = Instant.now()

            val response = client.aggregate(
                AggregateRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(start, end)
                )
            )
            val total = response[StepsRecord.COUNT_TOTAL] ?: 0L

            if (baselineDate != todayKey) {
                // First read today (or first read after day rollover).
                baselineDate = todayKey
                baselineTotal = total
                lastSeenTotal = total
                Logger.log(context, "$TAG: baseline for $todayKey = $total")
                return 0
            }

            val delta = (total - lastSeenTotal).coerceAtLeast(0L)
            lastSeenTotal = total
            if (delta > 0) Logger.log(context, "$TAG: delta=$delta (total=$total)")
            delta.toInt()
        } catch (e: Exception) {
            Logger.log(context, "$TAG: aggregate failed: ${e.message}")
            0
        }
    }

    /** Called by StepSourceManager on day rollover to reset baseline. */
    fun onDayChanged() {
        baselineDate = null
        baselineTotal = 0L
        lastSeenTotal = 0L
    }
}