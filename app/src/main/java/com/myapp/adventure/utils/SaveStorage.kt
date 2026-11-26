package com.myapp.adventure.utils

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.net.toUri

/**
 * Ф5.6 (S2): the minimal file-tree both persistence layers use.
 *
 * Paths are relative to the user-picked adventure folder:
 * - autosave: `["progress.json"]`
 * - slots: `["saves", adventureKey, "slot-<uuid>.json"]`
 *
 * Keeping this interface free of Android types lets unit tests run the
 * real persistence logic over an in-memory tree and prove that autosave
 * and slots cannot touch each other.
 */
interface SaveStorage {
    fun readDocument(path: List<String>): String?
    fun writeDocument(path: List<String>, content: String): Boolean
    fun listDocuments(directory: List<String>): List<String>
    fun deleteDocument(path: List<String>): Boolean
}

/** SAF implementation over the persisted `adventure_folder_uri` tree. */
class SafSaveStorage private constructor(
    private val context: Context,
    val treeUriString: String,
) : SaveStorage {

    private val treeUri: Uri = treeUriString.toUri()

    override fun readDocument(path: List<String>): String? {
        val parentDocId = resolveDirectory(path.dropLast(1), create = false) ?: return null
        val entry = findChild(parentDocId, path.last()) ?: return null
        return try {
            context.contentResolver.openInputStream(entry.uri)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
        } catch (e: Exception) {
            Logger.log(context, "Failed to read '${path.joinToString("/")}': ${e.message}")
            null
        }
    }

    override fun writeDocument(path: List<String>, content: String): Boolean {
        val parentDocId = resolveDirectory(path.dropLast(1), create = true) ?: return false
        val name = path.last()
        val existing = findChild(parentDocId, name)
        val fileUri = existing?.uri ?: try {
            DocumentsContract.createDocument(
                context.contentResolver,
                docUri(parentDocId),
                "application/json",
                name,
            )
        } catch (e: Exception) {
            Logger.log(context, "Failed to create '${path.joinToString("/")}': ${e.message}")
            null
        } ?: return false

        return try {
            context.contentResolver.openOutputStream(fileUri, "wt")?.use { out ->
                out.write(content.toByteArray(Charsets.UTF_8))
            } ?: return false
            true
        } catch (e: Exception) {
            Logger.log(context, "Failed to write '${path.joinToString("/")}': ${e.message}")
            false
        }
    }

    override fun listDocuments(directory: List<String>): List<String> {
        val docId = resolveDirectory(directory, create = false) ?: return emptyList()
        return children(docId)
            .filter { it.mimeType != DocumentsContract.Document.MIME_TYPE_DIR }
            .map { it.name }
    }

    override fun deleteDocument(path: List<String>): Boolean {
        val parentDocId = resolveDirectory(path.dropLast(1), create = false) ?: return false
        val entry = findChild(parentDocId, path.last()) ?: return false
        return try {
            DocumentsContract.deleteDocument(context.contentResolver, entry.uri)
        } catch (e: Exception) {
            Logger.log(context, "Failed to delete '${path.joinToString("/")}': ${e.message}")
            false
        }
    }

    // ==================== SAF helpers ====================

    private data class DocumentEntry(
        val name: String,
        val docId: String,
        val uri: Uri,
        val mimeType: String?,
    )

    private fun resolveDirectory(directory: List<String>, create: Boolean): String? {
        var docId = DocumentsContract.getTreeDocumentId(treeUri)
        for (name in directory) {
            val child = findChild(docId, name)
            if (child != null) {
                docId = child.docId
                continue
            }
            if (!create) return null
            docId = createDirectory(docId, name) ?: return null
        }
        return docId
    }

    private fun findChild(parentDocId: String, displayName: String): DocumentEntry? =
        children(parentDocId).firstOrNull { it.name == displayName }

    private fun children(parentDocId: String): List<DocumentEntry> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        val result = mutableListOf<DocumentEntry>()
        try {
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                )
                val nameIndex = cursor.getColumnIndexOrThrow(
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                )
                val mimeIndex = cursor.getColumnIndex(
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                )
                while (cursor.moveToNext()) {
                    val docId = cursor.getString(idIndex) ?: continue
                    val name = cursor.getString(nameIndex) ?: continue
                    val mime = if (mimeIndex >= 0) cursor.getString(mimeIndex) else null
                    result += DocumentEntry(name, docId, docUri(docId), mime)
                }
            }
        } catch (e: Exception) {
            Logger.log(context, "Failed to list '$parentDocId': ${e.message}")
        }
        return result
    }

    private fun createDirectory(parentDocId: String, displayName: String): String? =
        try {
            val created = DocumentsContract.createDocument(
                context.contentResolver,
                docUri(parentDocId),
                DocumentsContract.Document.MIME_TYPE_DIR,
                displayName,
            )
            created?.let { DocumentsContract.getDocumentId(it) }
        } catch (e: Exception) {
            Logger.log(context, "Failed to create directory '$displayName': ${e.message}")
            null
        }

    private fun docUri(docId: String): Uri =
        DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)

    companion object {
        private const val PREFS_NAME = "adventure_prefs"
        private const val PREF_FOLDER_URI = "adventure_folder_uri"

        fun forBaseFolder(context: Context): SafSaveStorage? {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val uriString = prefs.getString(PREF_FOLDER_URI, null) ?: return null
            return SafSaveStorage(context.applicationContext, uriString)
        }
    }
}
