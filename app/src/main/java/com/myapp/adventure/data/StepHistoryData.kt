package com.myapp.adventure.data

/**
 * One day's worth of steps.
 *
 * @param date  local date in ISO format ("2026-11-04")
 * @param steps delta for this day (not an absolute sensor reading)
 */
data class StepBucket(
    val date: String,
    val steps: Int
)

/**
 * Persisted step history.
 *
 * [buckets] are sorted by [StepBucket.date] ascending and capped at 30 entries
 * (older entries are dropped). [totalStepsAllTime] is a monotonic counter that
 * is never trimmed and is independent of [buckets].
 */
data class StepHistoryData(
    val totalStepsAllTime: Long = 0L,
    val buckets: List<StepBucket> = emptyList()
)