package com.myapp.adventure.utils

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Thread-safe, non-blocking file logger.
 *
 * - [log] only appends to an in-memory queue and schedules a batched flush.
 *   Never touches disk on the calling thread.
 * - Batched flush writes to the file with `OutputStream(uri, "wa")` (append).
 *   No read-modify-write cycle, so cost per flush is O(buffered lines), not O(file size).
 * - Timestamps use [Locale.ROOT] and an immutable [DateTimeFormatter], so logs
 *   are stable across locale changes and safe from multiple threads.
 * - The log file [Uri] is resolved once and cached; reset on [init] or when
 *   the file disappears.
 *
 * Read/clear are `suspend` and run on [Dispatchers.IO]; callers must not
 * assume they can be called from the main thread without dispatching.
 */
object Logger {

    private const val PREFS_NAME = "adventure_prefs"
    private const val KEY_LOGGING_ENABLED = "logging_enabled"
    private const val LOG_FILE_NAME = "debug_log.txt"
    private const val FLUSH_DEBOUNCE_MS = 2_000L
    private const val MAX_BUFFER_LINES = 100

    private val dateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT)

    @Volatile private var baseUri: Uri? = null
    @Volatile private var enabled: Boolean = false

    /** Cached log file URI. */
    @Volatile private var logFileUri: Uri? = null

    /** In-memory buffer of lines waiting to be flushed. */
    private val pendingLines = ConcurrentLinkedQueue<String>()

    /** Serialises flushes: at most one write to disk at a time. */
    private val flushMutex = Mutex()

    /** True while a debounced flush is already scheduled. */
    private val flushScheduled = AtomicBoolean(false)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ==================== Lifecycle ====================

    fun init(context: Context, folderUriString: String) {
        baseUri = folderUriString.toUri()
        enabled = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_LOGGING_ENABLED, true)
        // Base URI may have changed; cached file URI is no longer valid.
        logFileUri = null
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        this.enabled = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_LOGGING_ENABLED, enabled)
            .apply()
        if (!enabled) {
            // Don't silently drop pending lines when logging is disabled.
            val appContext = context.applicationContext
            scope.launch { flushLocked(appContext) }
        }
    }

    // ==================== Log (non-blocking) ====================

    /**
     * Fast, thread-safe, non-blocking. Appends [message] to the in-memory
     * buffer and schedules a batched flush. Never performs disk I/O on the
     * calling thread, so it is safe to call from `onSensorChanged`,
     * `onStartCommand`, UI callbacks, etc.
     */
    fun log(context: Context, message: String) {
        if (!enabled || baseUri == null) return
        val timestamp = dateFormatter.format(LocalDateTime.now())
        pendingLines.add("$timestamp $message")

        val appContext = context.applicationContext
        if (pendingLines.size >= MAX_BUFFER_LINES) {
            // Buffer is filling up fast — flush now, no debounce.
            scope.launch { flushLocked(appContext) }
        } else {
            scheduleFlush(appContext)
        }
    }

    private fun scheduleFlush(appContext: Context) {
        if (!flushScheduled.compareAndSet(false, true)) return
        scope.launch {
            delay(FLUSH_DEBOUNCE_MS)
            flushScheduled.set(false)
            flushLocked(appContext)
        }
    }

    /** Force-drain the buffer to disk. Safe to call from any thread. */
    suspend fun flush(context: Context) {
        val appContext = context.applicationContext
        withContext(Dispatchers.IO) { flushLocked(appContext) }
    }

    // ==================== Read / clear (suspend) ====================

    /**
     * Returns the full log content. Flushes the buffer first so the caller
     * sees the latest lines. Runs on [Dispatchers.IO].
     */
    suspend fun getLogContent(context: Context): String {
        if (baseUri == null) return "Логирование не настроено"
        val appContext = context.applicationContext
        return withContext(Dispatchers.IO) {
            flushLocked(appContext)
            val fileUri = getOrCreateLogFile(appContext)
                ?: return@withContext "Файл лога недоступен"
            try {
                appContext.contentResolver.openInputStream(fileUri)
                    ?.bufferedReader()
                    ?.use { it.readText() }
                    ?: "Лог пуст"
            } catch (e: Exception) {
                "Ошибка чтения: ${e.message}"
            }
        }
    }

    /**
     * Drops all pending lines and truncates the log file to zero bytes.
     * Runs on [Dispatchers.IO].
     */
    suspend fun clearLog(context: Context) {
        val appContext = context.applicationContext
        withContext(Dispatchers.IO) {
            pendingLines.clear()
            val fileUri = getOrCreateLogFile(appContext) ?: return@withContext
            try {
                appContext.contentResolver.openOutputStream(fileUri, "wt")?.use {
                    it.write(ByteArray(0))
                }
            } catch (_: Exception) {
            }
        }
    }

    // ==================== Internal ====================

    /**
     * Drains [pendingLines] and appends them to the log file in a single
     * open/write/close. OutputStream opened in append mode ("wa"), so the
     * file is never read back. Must be called under [flushMutex].
     */
    private suspend fun flushLocked(context: Context) {
        flushMutex.withLock {
            if (pendingLines.isEmpty()) return
            val fileUri = getOrCreateLogFile(context) ?: return

            // Snapshot and clear while under lock to keep ordering stable.
            val sb = StringBuilder()
            while (true) {
                val line = pendingLines.poll() ?: break
                sb.append(line).append('\n')
            }
            if (sb.isEmpty()) return

            try {
                context.contentResolver.openOutputStream(fileUri, "wa")?.use { out ->
                    out.write(sb.toString().toByteArray())
                }
            } catch (e: Exception) {
                // Lines are already dropped from the buffer; worst case we lose logs.
                android.util.Log.e("Logger", "Failed to flush log lines: ${e.message}")
            }
        }
    }

    private fun getOrCreateLogFile(context: Context): Uri? {
        // Fast path: cached URI is still usable.
        logFileUri?.let { return it }

        val folderUri = baseUri ?: return null
        val parentDocId = DocumentsContract.getTreeDocumentId(folderUri)
        val rootDocUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, parentDocId)

        val childrenUri =
            DocumentsContract.buildChildDocumentsUriUsingTree(folderUri, parentDocId)
        val cursor = context.contentResolver.query(childrenUri, null, null, null, null)
        cursor?.use {
            while (it.moveToNext()) {
                val name = it.getString(
                    it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                )
                if (name == LOG_FILE_NAME) {
                    val docId = it.getString(
                        it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    )
                    val uri = DocumentsContract.buildDocumentUriUsingTree(folderUri, docId)
                    logFileUri = uri
                    return uri
                }
            }
        }

        return try {
            val created = DocumentsContract.createDocument(
                context.contentResolver,
                rootDocUri,
                "text/plain",
                LOG_FILE_NAME
            )
            logFileUri = created
            created
        } catch (_: Exception) {
            null
        }
    }
}