package com.myapp.adventure.utils

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.myapp.adventure.data.ProgressData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Persists per-adventure progress in `progress.json` inside the adventure folder.
 *
 * Design:
 * - The whole progress map is kept in memory after the first read. Reads never hit the disk again.
 * - [saveProgress] and [deleteProgress] only update the in-memory cache and schedule a batched flush.
 * - [flush] forces the cache to disk; callers use it at `onStop` to guarantee persistence.
 * - Disk is written at most once per [SAVE_DEBOUNCE_MS] when progress changes.
 * - Logging happens only on real state changes (save with different value, delete of existing entry,
 *   flush that actually wrote) to keep the log meaningful.
 *
 * Ф5.6: the cache/file logic lives in [ProgressStore] over [SaveStorage];
 * this object only owns the Android context, debounce scope and file-name
 * resolution. That keeps the exact `onPause`/`onStop` save/flush sequence
 * unit-testable.
 */
object ProgressManager {

    private const val SAVE_DEBOUNCE_MS = 2_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val storeLock = Any()

    @Volatile private var store: ProgressStore? = null
    @Volatile private var storeTreeUri: String? = null

    /** Set while a debounced flush is already scheduled. */
    private val flushScheduled = AtomicBoolean(false)

    // ==================== Public API ====================

    /**
     * Returns saved progress for [adventureFileUri], or null if none.
     *
     * The first call reads the whole progress.json once; subsequent calls are pure in-memory.
     * Prefer [warmUp] during app startup so this fast path is already primed.
     */
    fun loadProgress(context: Context, adventureFileUri: Uri): ProgressData? {
        val fileName = getFileNameFromUri(adventureFileUri) ?: return null
        return store(context)?.load(fileName)
    }

    /**
     * Schedules a save of [progress] for [adventureFileUri]. Does not touch the disk
     * synchronously; a batched flush happens within [SAVE_DEBOUNCE_MS] or at the next [flush].
     */
    fun saveProgress(context: Context, adventureFileUri: Uri, progress: ProgressData) {
        val fileName = getFileNameFromUri(adventureFileUri) ?: return
        val target = store(context) ?: return
        if (!target.save(fileName, progress)) return

        Logger.log(
            context,
            "Progress saved for $fileName: chapter ${progress.currentChapterIndex + 1}, " +
                    "scene '${progress.currentSceneId}', gate=${progress.isGateActive}, " +
                    "remaining=${progress.remainingSteps}, lastAbsAtSave=${progress.lastAbsoluteAtSave}"
        )

        scheduleFlush(context.applicationContext)
    }

    /** Removes progress for [adventureFileUri]. No-op if nothing was saved. */
    fun deleteProgress(context: Context, adventureFileUri: Uri) {
        val fileName = getFileNameFromUri(adventureFileUri) ?: return
        val target = store(context) ?: return
        if (!target.delete(fileName)) return

        Logger.log(context, "Progress deleted for $fileName")
        scheduleFlush(context.applicationContext)
    }

    /**
     * Forces the current in-memory cache to disk. Call this from `onStop`
     * ("left the foreground") rather than `onPause` (which fires before many transient states).
     */
    suspend fun flush(context: Context) {
        val appContext = context.applicationContext
        withContext(Dispatchers.IO) { store(appContext)?.flush() }
    }

    /**
     * Reads progress.json into the in-memory cache if not already loaded.
     * Call once during startup to avoid a synchronous disk read on a hot path.
     */
    suspend fun warmUp(context: Context) {
        val appContext = context.applicationContext
        withContext(Dispatchers.IO) { store(appContext)?.warmUp() }
    }

    // ==================== Internal ====================

    /**
     * Returns the cache for the currently selected adventure folder,
     * recreating it when the user picks a different folder.
     */
    private fun store(context: Context): ProgressStore? {
        val appContext = context.applicationContext
        val storage = SafSaveStorage.forBaseFolder(appContext) ?: return null
        synchronized(storeLock) {
            val current = store
            if (current != null && storeTreeUri == storage.treeUriString) return current
            if (current != null) {
                // The user picked another folder: do not lose pending
                // debounced writes in the previous cache.
                scope.launch { current.flush() }
            }
            val created = ProgressStore(storage) { message ->
                Logger.log(appContext, message)
            }
            store = created
            storeTreeUri = storage.treeUriString
            return created
        }
    }

    private fun scheduleFlush(appContext: Context) {
        if (!flushScheduled.compareAndSet(false, true)) return
        scope.launch {
            delay(SAVE_DEBOUNCE_MS)
            flushScheduled.set(false)
            store(appContext)?.flush()
        }
    }

    private fun getFileNameFromUri(uri: Uri): String? = try {
        DocumentsContract.getDocumentId(uri)
            .substringAfterLast("/")
            .substringAfterLast(":")
    } catch (e: Exception) {
        null
    }
}
