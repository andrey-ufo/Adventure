package com.myapp.adventure.step

import android.content.Context
import android.os.Build
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
 * Reports today's HC step total as a series of deltas. The "last seen total"
 * and the date it belongs to are persisted to SharedPreferences so that:
 *  - the first poll of a new day returns *today's full aggregate* (captures
 *    all steps the user took since midnight, not just steps after app launch);
 *  - subsequent polls of the same day return only new steps;
 *  - process restarts mid-day do not reset the baseline (no step loss).
 */
class HealthConnectStepSource : StepSource {

    companion object {
        private const val TAG = "HealthConnectStepSource"
        const val PROVIDER_PACKAGE = "com.google.android.apps.healthdata"

        private const val HC_PREFS = "hc_step_source_prefs"
        private const val KEY_LAST_SEEN_DATE = "last_seen_date"
        private const val KEY_LAST_SEEN_TOTAL = "last_seen_total"
    }

    override val id: String = "health_connect"

    override fun isAvailable(context: Context): Boolean {
        // On Android 14+ (SDK 34) Health Connect is a system service and the
        // legacy provider package `com.google.android.apps.healthdata` is not
        // installed. Passing that package name makes getSdkStatus() return
        // SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED and effectively disables HC.
        val status = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            HealthConnectClient.getSdkStatus(context)
        } else {
            HealthConnectClient.getSdkStatus(context, PROVIDER_PACKAGE)
        }
        return status == HealthConnectClient.SDK_AVAILABLE
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
        if (!hasReadPermission(context)) {
            // Logged once at poll level by StepSourceManager as permission_missing;
            // no need to spam per-poll here.
            return 0
        }

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

            val prefs = context.getSharedPreferences(HC_PREFS, Context.MODE_PRIVATE)
            val savedDate = prefs.getString(KEY_LAST_SEEN_DATE, null)
            val savedTotal = prefs.getLong(KEY_LAST_SEEN_TOTAL, 0L)

            val lastSeen = if (savedDate == todayKey) savedTotal else 0L
            val delta = (total - lastSeen).coerceAtLeast(0L)

            prefs.edit()
                .putString(KEY_LAST_SEEN_DATE, todayKey)
                .putLong(KEY_LAST_SEEN_TOTAL, total)
                .apply()

            // Log on first poll of the day (or when delta arrives). Normal
            // "no new steps since last poll" is silent.
            if (delta > 0 || savedDate != todayKey) {
                Logger.log(
                    context,
                    "$TAG: HC todayTotal=$total, lastSeen=$lastSeen, delta=$delta"
                )
            }
            delta.toInt()
        } catch (e: Exception) {
            Logger.log(context, "$TAG: aggregate failed: ${e.message}")
            0
        }
    }

    /** Called by StepSourceManager on day rollover to reset baseline. */
    fun onDayChanged(context: Context) {
        context.getSharedPreferences(HC_PREFS, Context.MODE_PRIVATE)
            .edit().clear().apply()
    }
}