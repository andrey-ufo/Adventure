package com.myapp.adventure.utils

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.myapp.adventure.data.ProgressData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
 */
object ProgressManager {

    private const val PROGRESS_FILE_NAME = "progress.json"
    private const val SAVE_DEBOUNCE_MS = 2_000L

    private val gson = Gson()
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Guards reads/writes of [cache] and [dirty]. */
    private val cacheLock = Any()

    /** In-memory copy of progress.json keyed by adventure file name. */
    @Volatile private var cache: Map<String, ProgressData>? = null

    /** True when [cache] has unsaved changes that must be flushed. */
    @Volatile private var dirty = false

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
        val fileName = getFileNameFromUri(context, adventureFileUri) ?: return null
        ensureLoaded(context)
        return cache?.get(fileName)
    }

    /**
     * Schedules a save of [progress] for [adventureFileUri]. Does not touch the disk
     * synchronously; a batched flush happens within [SAVE_DEBOUNCE_MS] or at the next [flush].
     */
    fun saveProgress(context: Context, adventureFileUri: Uri, progress: ProgressData) {
        val fileName = getFileNameFromUri(context, adventureFileUri) ?: return
        val logged = synchronized(cacheLock) {
            ensureLoaded(context)
            val updated = (cache ?: emptyMap()).toMutableMap()
            val previous = updated[fileName]
            if (previous == progress) return  // No-op, keep cache clean.
            updated[fileName] = progress
            cache = updated
            dirty = true
            true
        }
        if (!logged) return

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
        val fileName = getFileNameFromUri(context, adventureFileUri) ?: return
        val removed = synchronized(cacheLock) {
            ensureLoaded(context)
            val current = cache ?: return
            if (!current.containsKey(fileName)) return
            val updated = current.toMutableMap()
            updated.remove(fileName)
            cache = updated
            dirty = true
            true
        }
        if (!removed) return

        Logger.log(context, "Progress deleted for $fileName")
        scheduleFlush(context.applicationContext)
    }

    /**
     * Forces the current in-memory cache to disk. Call this from `onStop`
     * ("left the foreground") rather than `onPause` (which fires before many transient states).
     */
    suspend fun flush(context: Context) {
        val appContext = context.applicationContext
        withContext(Dispatchers.IO) { flushLocked(appContext) }
    }

    /**
     * Reads progress.json into the in-memory cache if not already loaded.
     * Call once during startup to avoid a synchronous disk read on a hot path.
     */
    suspend fun warmUp(context: Context) {
        val appContext = context.applicationContext
        withContext(Dispatchers.IO) { ensureLoaded(appContext) }
    }

    // ==================== Internal ====================

    private fun ensureLoaded(context: Context) {
        if (cache != null) return
        synchronized(cacheLock) {
            if (cache != null) return
            cache = readFromDisk(context)
        }
    }

    private fun readFromDisk(context: Context): Map<String, ProgressData> {
        val fileUri = getProgressFileUri(context) ?: return emptyMap()
        return try {
            val json = context.contentResolver.openInputStream(fileUri)
                ?.bufferedReader()
                ?.use { it.readText() }
            if (json.isNullOrBlank()) {
                Logger.log(context, "Progress cache loaded: empty file")
                emptyMap()
            } else {
                val type = object : TypeToken<Map<String, ProgressData>>() {}.type
                val map = gson.fromJson<Map<String, ProgressData>>(json, type) ?: emptyMap()
                Logger.log(context, "Progress cache loaded: ${map.size} entries")
                map
            }
        } catch (e: Exception) {
            Logger.log(context, "Failed to read progress.json: ${e.message}")
            emptyMap()
        }
    }

    private fun scheduleFlush(appContext: Context) {
        if (!flushScheduled.compareAndSet(false, true)) return
        scope.launch {
            delay(SAVE_DEBOUNCE_MS)
            flushScheduled.set(false)
            flushLocked(appContext)
        }
    }

    /** Writes the cache to disk if dirty. Safe for concurrent calls (serialised by [mutex]). */
    private suspend fun flushLocked(context: Context) {
        mutex.withLock {
            val snapshot: Map<String, ProgressData>
            synchronized(cacheLock) {
                if (!dirty) return
                snapshot = cache ?: return
            }
            val fileUri = getOrCreateProgressFile(context)
            if (fileUri == null) {
                Logger.log(context, "Cannot create or access progress.json")
                return
            }
            try {
                val json = gson.toJson(snapshot)
                context.contentResolver.openOutputStream(fileUri, "wt")?.use { out ->
                    out.write(json.toByteArray())
                }
                synchronized(cacheLock) { dirty = false }
                Logger.log(context, "Progress cache flushed: ${snapshot.size} entries")
            } catch (e: Exception) {
                Logger.log(context, "Failed to write progress.json: ${e.message}")
            }
        }
    }

    // ==================== File access ====================

    private fun getOrCreateProgressFile(context: Context): Uri? {
        val existing = getProgressFileUri(context)
        if (existing != null) return existing
        val folderUri = getBaseFolderUri(context) ?: return null
        val parentDocId = DocumentsContract.getTreeDocumentId(folderUri)
        val rootDocUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, parentDocId)
        return try {
            DocumentsContract.createDocument(
                context.contentResolver,
                rootDocUri,
                "application/json",
                PROGRESS_FILE_NAME
            )
        } catch (e: Exception) {
            Logger.log(context, "Failed to create progress.json: ${e.message}")
            null
        }
    }

    private fun getProgressFileUri(context: Context): Uri? {
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
                if (name == PROGRESS_FILE_NAME) {
                    val docId = it.getString(
                        it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    )
                    return DocumentsContract.buildDocumentUriUsingTree(folderUri, docId)
                }
            }
        }
        return null
    }

    private fun getFileNameFromUri(context: Context, uri: Uri): String? {
        val docId = DocumentsContract.getDocumentId(uri)
        return docId.substringAfterLast("/").substringAfterLast(":")
    }

    private fun getBaseFolderUri(context: Context): Uri? {
        val prefs = context.getSharedPreferences("adventure_prefs", Context.MODE_PRIVATE)
        val uriString = prefs.getString("adventure_folder_uri", null) ?: return null
        return Uri.parse(uriString)
    }
}