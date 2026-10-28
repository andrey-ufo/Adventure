package com.myapp.adventure.utils

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Logger {
    private var baseUri: Uri? = null
    private var enabled: Boolean = false
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    fun init(context: Context, folderUriString: String) {
        baseUri = Uri.parse(folderUriString)
        enabled = context.getSharedPreferences("adventure_prefs", Context.MODE_PRIVATE)
            .getBoolean("logging_enabled", true)   // теперь по умолчанию true
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        this.enabled = enabled
        context.getSharedPreferences("adventure_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("logging_enabled", enabled)
            .apply()
    }

    fun log(context: Context, message: String) {
        if (!enabled || baseUri == null) return
        val timestamp = dateFormat.format(Date())
        val line = "$timestamp $message\n"
        try {
            val fileUri = getOrCreateLogFile(context) ?: return
            val existingText = try {
                context.contentResolver.openInputStream(fileUri)?.bufferedReader()?.use { it.readText() } ?: ""
            } catch (_: Exception) { "" }
            val newText = existingText + line
            context.contentResolver.openOutputStream(fileUri, "wt")?.use { outputStream ->
                outputStream.write(newText.toByteArray())
            }
        } catch (_: Exception) { }
    }

    fun getLogContent(context: Context): String {
        if (baseUri == null) return "Логирование не настроено"
        val fileUri = getOrCreateLogFile(context) ?: return "Файл лога недоступен"
        return try {
            context.contentResolver.openInputStream(fileUri)?.bufferedReader()?.use { it.readText() } ?: "Лог пуст"
        } catch (e: Exception) {
            "Ошибка чтения: ${e.message}"
        }
    }

    fun clearLog(context: Context) {
        val fileUri = getOrCreateLogFile(context) ?: return
        try {
            context.contentResolver.openOutputStream(fileUri, "wt")?.use { outputStream ->
                outputStream.write("".toByteArray())
            }
        } catch (_: Exception) { }
    }

    private fun getOrCreateLogFile(context: Context): Uri? {
        val folderUri = baseUri ?: return null
        val parentDocId = DocumentsContract.getTreeDocumentId(folderUri)
        val rootDocUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, parentDocId)

        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(folderUri, parentDocId)
        val cursor = context.contentResolver.query(childrenUri, null, null, null, null)
        cursor?.use {
            while (it.moveToNext()) {
                val name = it.getString(it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME))
                if (name == "debug_log.txt") {
                    val docId = it.getString(it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID))
                    return DocumentsContract.buildDocumentUriUsingTree(folderUri, docId)
                }
            }
        }

        return try {
            DocumentsContract.createDocument(
                context.contentResolver,
                rootDocUri,
                "text/plain",
                "debug_log.txt"
            )
        } catch (e: Exception) {
            null
        }
    }
}