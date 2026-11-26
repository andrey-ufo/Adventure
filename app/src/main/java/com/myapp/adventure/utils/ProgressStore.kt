package com.myapp.adventure.utils

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.myapp.adventure.data.ProgressData
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Ф5.6: pure autosave persistence (cache + `progress.json`) over
 * [SaveStorage].
 *
 * This is the same logic `ProgressManager` used inline, moved behind the
 * storage interface so unit tests can run the exact save/flush sequence
 * that `ReaderRoute` triggers on `ON_PAUSE` / `ON_STOP`.
 *
 * Autosave only ever touches [PROGRESS_FILE_NAME]; slot paths are handled
 * by [SlotStore]. The split is enforced by the paths, not by a runtime
 * check.
 */
internal class ProgressStore(
    private val storage: SaveStorage,
    private val log: (String) -> Unit = {},
) {

    private val gson = Gson()
    private val mutex = Mutex()

    /** Guards reads/writes of [cache], [dirty] and [mutationVersion]. */
    private val cacheLock = Any()

    /** In-memory copy of progress.json keyed by adventure file name. */
    @Volatile private var cache: Map<String, ProgressData>? = null

    /** True when [cache] has unsaved changes that must be flushed. */
    @Volatile private var dirty = false

    /** Incremented by every in-memory mutation. */
    private var mutationVersion = 0L

    /** Entries that failed migration (unknown/future version). */
    @Volatile private var invalidEntries: Map<String, String> = emptyMap()

    fun load(fileName: String): ProgressData? {
        ensureLoaded()
        invalidEntries[fileName]?.let { reason ->
            throw ProgressMigrationException(
                "Не удалось открыть сохранение '$fileName': $reason",
            )
        }
        return cache?.get(fileName)
    }

    /**
     * Updates the cache. Returns true when the value actually changed and
     * a flush is needed.
     */
    fun save(fileName: String, progress: ProgressData): Boolean {
        val normalized = try {
            ProgressMigration.migrate(progress)
        } catch (e: ProgressMigrationException) {
            log("Refusing to save progress: ${e.message}")
            return false
        }
        return synchronized(cacheLock) {
            ensureLoaded()
            val updated = (cache ?: emptyMap()).toMutableMap()
            if (updated[fileName] == normalized) return@synchronized false
            updated[fileName] = normalized
            cache = updated
            dirty = true
            mutationVersion++
            true
        }
    }

    /** Removes an entry. Returns true when something was removed. */
    fun delete(fileName: String): Boolean = synchronized(cacheLock) {
        ensureLoaded()
        val current = cache ?: return@synchronized false
        if (!current.containsKey(fileName)) return@synchronized false
        val updated = current.toMutableMap()
        updated.remove(fileName)
        cache = updated
        dirty = true
        mutationVersion++
        true
    }

    suspend fun flush() {
        mutex.withLock {
            val (snapshot, writtenVersion) = synchronized(cacheLock) {
                if (!dirty) return@withLock
                val current = cache ?: return@withLock
                current to mutationVersion
            }
            val json = gson.toJson(snapshot)
            if (!storage.writeDocument(listOf(PROGRESS_FILE_NAME), json)) {
                log("Failed to write progress.json")
                return@withLock
            }
            synchronized(cacheLock) {
                // A save that arrived during the write must stay dirty.
                if (mutationVersion == writtenVersion) dirty = false
            }
            log("Progress cache flushed: ${snapshot.size} entries")
        }
    }

    suspend fun warmUp() {
        ensureLoaded()
    }

    private fun ensureLoaded() {
        if (cache != null) return
        synchronized(cacheLock) {
            if (cache != null) return
            cache = readFromDisk()
        }
    }

    private fun readFromDisk(): Map<String, ProgressData> {
        val json = storage.readDocument(listOf(PROGRESS_FILE_NAME))
        if (json.isNullOrBlank()) {
            log("Progress cache loaded: empty file")
            return emptyMap()
        }
        return try {
            val type = object : TypeToken<Map<String, ProgressData>>() {}.type
            val raw = gson.fromJson<Map<String, ProgressData>>(json, type) ?: emptyMap()
            val valid = mutableMapOf<String, ProgressData>()
            val invalid = mutableMapOf<String, String>()
            for ((name, data) in raw) {
                try {
                    valid[name] = ProgressMigration.migrate(data)
                } catch (e: ProgressMigrationException) {
                    invalid[name] = e.message ?: "unknown migration error"
                    log("Progress migration failed for $name: ${e.message}")
                }
            }
            invalidEntries = invalid
            log("Progress cache loaded: ${valid.size} valid, ${invalid.size} invalid entries")
            valid
        } catch (e: Exception) {
            log("Failed to read progress.json: ${e.message}")
            emptyMap()
        }
    }

    companion object {
        const val PROGRESS_FILE_NAME = "progress.json"
    }
}
