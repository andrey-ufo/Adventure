package com.myapp.adventure.utils

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.myapp.adventure.data.StepBucket
import com.myapp.adventure.data.StepHistoryData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Owns the step history. Single writer, in-memory cache, batched disk flush.
 *
 * Data model:
 * - [StepHistoryData.buckets] — up to 30 daily deltas, oldest first, trimmed automatically.
 * - [StepHistoryData.totalStepsAllTime] — monotonic counter that never trims.
 *
 * Public API for gameplay:
 * - [getTotalSteps]          — all-time monotonic counter.
 * - [getTodaySteps]          — today's delta.
 * - [getLastNDaysSum]        — rolling window sum (7 / 30 days, achievements).
 * - [getDailyBuckets]        — last N days as a list (statistics screen).
 * - [recordDelta]            — the only writer entry point, source-agnostic.
 * - [flush]                  — force-persist.
 *
 * Future features (achievements, step-gated choices, adventure unlocks) must read
 * history through the public getters above — never touch files or [StepCounterService].
 * TODO(feature:achievements): a StepAchievementEngine will subscribe to history changes
 *   (e.g. an in-process callback invoked from [recordDelta]) and evaluate rules like
 *   "≥50 000 steps in the last 7 days".
 */
object StepHistoryManager {

    private const val HISTORY_FILE_NAME = "step_history.json"
    private const val MAX_DAYS = 30
    private const val SAVE_DEBOUNCE_MS = 10_000L

    private val gson = Gson()
    private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val mutex = Mutex()

    // ----- In-memory cache -----

    @Volatile private var cache: StepHistoryData = StepHistoryData()
    @Volatile private var cacheLoaded = false
    @Volatile private var lastSaveAt: Long = 0L

    // ==================== Public API ====================

    /** Total steps ever recorded by the app (monotonic, independent of buckets). */
    fun getTotalSteps(context: Context): Long {
        ensureLoaded(context)
        return cache.totalStepsAllTime
    }

    /** Steps recorded today (local date). */
    fun getTodaySteps(context: Context): Int {
        ensureLoaded(context)
        val today = LocalDate.now().format(dateFormatter)
        return cache.buckets.firstOrNull { it.date == today }?.steps ?: 0
    }

    /** Sum of the last [days] buckets (including today). */
    fun getLastNDaysSum(context: Context, days: Int): Int {
        if (days <= 0) return 0
        ensureLoaded(context)
        val cutoff = LocalDate.now()
            .minusDays((days - 1).toLong())
            .format(dateFormatter)
        return cache.buckets
            .filter { it.date >= cutoff }
            .sumOf { it.steps }
    }

    /**
     * Last [lastNDays] buckets, oldest first, padded with zero-step entries
     * for days without data. Length is exactly [lastNDays].
     */
    fun getDailyBuckets(context: Context, lastNDays: Int): List<StepBucket> {
        if (lastNDays <= 0) return emptyList()
        ensureLoaded(context)
        val today = LocalDate.now()
        val result = ArrayList<StepBucket>(lastNDays)
        for (i in (lastNDays - 1) downTo 0) {
            val date = today.minusDays(i.toLong()).format(dateFormatter)
            val existing = cache.buckets.firstOrNull { it.date == date }
            result.add(existing ?: StepBucket(date, 0))
        }
        return result
    }

    /**
     * Record a delta of [delta] steps for today.
     * Non-positive deltas are ignored. Safe to call from any coroutine context.
     */
    suspend fun recordDelta(context: Context, delta: Int) {
        if (delta <= 0) return
        val appContext = context.applicationContext
        withContext(Dispatchers.IO) {
            mutex.withLock {
                ensureLoaded(appContext)
                applyDelta(appContext, delta)
                maybeFlush(appContext)
            }
        }
    }

    /** Force writing the current in-memory state to disk. */
    suspend fun flush(context: Context) {
        val appContext = context.applicationContext
        withContext(Dispatchers.IO) {
            mutex.withLock {
                ensureLoaded(appContext)
                saveToDisk(appContext, cache)
                lastSaveAt = System.currentTimeMillis()
            }
        }
    }

    /**
     * Apply a delta coming from Health Connect.
     *
     * Same semantics as [recordDelta] — increments today's bucket and the
     * monotonic total. Kept as a separate method so callers/logs can
     * distinguish HC-sourced steps.
     */
    suspend fun applyHealthConnectDelta(context: Context, delta: Int) {
        if (delta <= 0) return
        recordDelta(context, delta)
    }

    // ==================== Internal: load / migrate ====================

    private fun ensureLoaded(context: Context) {
        if (cacheLoaded) return
        synchronized(this) {
            if (cacheLoaded) return
            cache = loadFromDisk(context)
            cacheLoaded = true
        }
    }

    private fun loadFromDisk(context: Context): StepHistoryData {
        val fileUri = getHistoryFileUri(context) ?: return StepHistoryData()
        val json: String = try {
            context.contentResolver.openInputStream(fileUri)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()
        } catch (e: Exception) {
            Logger.log(context, "Failed to read step history: ${e.message}")
            return StepHistoryData()
        }
        if (json.isBlank()) return StepHistoryData()

        return try {
            val element = JsonParser.parseString(json)
            when {
                element.isJsonObject -> {
                    // New format: { "totalStepsAllTime": ..., "buckets": [...] }
                    gson.fromJson(element, StepHistoryData::class.java) ?: StepHistoryData()
                }
                element.isJsonArray -> {
                    // Legacy format: [["2026-11-04", 53000], ...] with absolute values
                    migrateLegacyFormat(context, element.asJsonArray)
                }
                else -> StepHistoryData()
            }
        } catch (e: Exception) {
            Logger.log(context, "Failed to parse step history: ${e.message}")
            StepHistoryData()
        }
    }

    /**
     * Convert legacy [["date", absoluteSteps], ...] to the new delta model.
     *
     * First bucket gets steps=0 because we don't know how many steps happened
     * before the first recorded value. [totalStepsAllTime] is set to the last
     * absolute reading — the closest approximation of "steps since phone boot",
     * which the user perceives as "all steps".
     */
    private fun migrateLegacyFormat(context: Context, array: JsonArray): StepHistoryData {
        val entries = mutableListOf<Pair<String, Int>>()
        for (el in array) {
            if (!el.isJsonArray) continue
            val pair = el.asJsonArray
            val date = safeString(pair, 0) ?: continue
            val abs = safeInt(pair, 1) ?: continue
            entries.add(date to abs)
        }
        entries.sortBy { it.first }

        val buckets = mutableListOf<StepBucket>()
        var prevAbs: Int? = null
        var lastAbs: Int = 0
        for ((date, abs) in entries) {
            val delta = if (prevAbs != null) (abs - prevAbs).coerceAtLeast(0) else 0
            buckets.add(StepBucket(date, delta))
            prevAbs = abs
            lastAbs = abs
        }
        val trimmed = trimBuckets(buckets)
        val total = lastAbs.toLong()

        Logger.log(
            context,
            "Migrated legacy step history: ${buckets.size} buckets, total=$total"
        )
        return StepHistoryData(totalStepsAllTime = total, buckets = trimmed)
    }

    private fun safeString(array: JsonArray, index: Int): String? {
        if (index < 0 || index >= array.size()) return null
        val el: JsonElement = array.get(index)
        return if (el.isJsonPrimitive) el.asString else null
    }

    private fun safeInt(array: JsonArray, index: Int): Int? {
        if (index < 0 || index >= array.size()) return null
        val el: JsonElement = array.get(index)
        return if (el.isJsonPrimitive && el.asJsonPrimitive.isNumber) el.asInt else null
    }

    // ==================== Internal: delta application ====================

    /**
     * Apply [delta] to today's bucket and bump the monotonic total.
     * Must be called under [mutex] after [ensureLoaded].
     */
    private fun applyDelta(context: Context, delta: Int) {
        if (delta <= 0) return
        val today = LocalDate.now().format(dateFormatter)
        val buckets = cache.buckets.toMutableList()
        val idx = buckets.indexOfFirst { it.date == today }
        if (idx >= 0) {
            buckets[idx] = buckets[idx].copy(steps = buckets[idx].steps + delta)
        } else {
            buckets.add(StepBucket(today, delta))
        }
        val trimmed = trimBuckets(buckets.sortedBy { it.date })
        cache = StepHistoryData(
            totalStepsAllTime = cache.totalStepsAllTime + delta,
            buckets = trimmed
        )
    }

    private fun trimBuckets(buckets: List<StepBucket>): List<StepBucket> {
        if (buckets.size <= MAX_DAYS) return buckets
        return buckets
            .sortedByDescending { it.date }
            .take(MAX_DAYS)
            .sortedBy { it.date }
    }

    // ==================== Internal: persistence ====================

    private fun maybeFlush(context: Context) {
        val now = System.currentTimeMillis()
        if (now - lastSaveAt >= SAVE_DEBOUNCE_MS) {
            saveToDisk(context, cache)
            lastSaveAt = now
        }
    }

    private fun saveToDisk(context: Context, data: StepHistoryData) {
        val fileUri = getOrCreateHistoryFile(context)
        if (fileUri == null) {
            Logger.log(context, "Cannot save step history: no history file")
            return
        }
        try {
            val json = gson.toJson(data)
            context.contentResolver.openOutputStream(fileUri, "wt")?.use {
                it.write(json.toByteArray())
            }
            Logger.log(
                context,
                "Step history saved: ${data.buckets.size} buckets, total=${data.totalStepsAllTime}"
            )
        } catch (e: Exception) {
            Logger.log(context, "Failed to save step history: ${e.message}")
        }
    }

    // ==================== Internal: file access ====================

    private fun getHistoryFileUri(context: Context): Uri? {
        val folderUri = getBaseFolderUri(context) ?: return null
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            folderUri,
            DocumentsContract.getTreeDocumentId(folderUri)
        )
        val cursor = context.contentResolver.query(childrenUri, null, null, null, null)
        cursor?.use {
            while (it.moveToNext()) {
                val name = it.getString(
                    it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                )
                if (name == HISTORY_FILE_NAME) {
                    val docId = it.getString(
                        it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    )
                    return DocumentsContract.buildDocumentUriUsingTree(folderUri, docId)
                }
            }
        }
        return null
    }

    private fun getOrCreateHistoryFile(context: Context): Uri? {
        val folderUri = getBaseFolderUri(context) ?: return null
        getHistoryFileUri(context)?.let { return it }

        val parentDocId = DocumentsContract.getTreeDocumentId(folderUri)
        val rootDocUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, parentDocId)
        return try {
            DocumentsContract.createDocument(
                context.contentResolver,
                rootDocUri,
                "application/json",
                HISTORY_FILE_NAME
            )
        } catch (e: Exception) {
            Logger.log(context, "Failed to create step_history.json: ${e.message}")
            null
        }
    }

    private fun getBaseFolderUri(context: Context): Uri? {
        val prefs = context.getSharedPreferences("adventure_prefs", Context.MODE_PRIVATE)
        val uriString = prefs.getString("adventure_folder_uri", null) ?: return null
        return Uri.parse(uriString)
    }
}