package com.myapp.adventure.utils

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import java.io.File

/**
 * Minimal SAF DocumentsProvider for Robolectric tests.
 *
 * Implements only what [SafSaveStorage] uses: children queries,
 * create/delete calls and file streams. Documents keep their bytes in
 * real temp files, so reads/writes behave like a provider rather than an
 * in-memory map.
 */
@Suppress("DEPRECATION")
class FakeSafProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "com.myapp.adventure.test.saf"
        private const val ROOT_ID = "root"

        private const val EXTRA_URI = "uri"
        private const val EXTRA_MIME_TYPE = "mime_type"
        private const val EXTRA_DISPLAY_NAME = "_display_name"

        private const val METHOD_CREATE_DOCUMENT = "android:createDocument"
        private const val METHOD_DELETE_DOCUMENT = "android:deleteDocument"
    }

    private data class Node(
        val id: String,
        val name: String,
        val mime: String,
        val parentId: String?,
        val file: File?,
    )

    private val nodes = linkedMapOf<String, Node>()
    private var nextId = 1
    private lateinit var baseDir: File

    lateinit var treeUri: Uri
        private set

    override fun onCreate(): Boolean {
        val context = requireNotNull(context)
        baseDir = File(context.cacheDir, "fake-saf-${System.nanoTime()}").apply { mkdirs() }
        nodes[ROOT_ID] = Node(ROOT_ID, "root", DocumentsContract.Document.MIME_TYPE_DIR, null, null)
        treeUri = DocumentsContract.buildTreeDocumentUri(AUTHORITY, ROOT_ID)
        return true
    }

    fun debugNodes(): String = nodes.values.joinToString { "${it.id}:${it.name}<${it.parentId}" }

    fun createFile(name: String): Uri {
        // Real providers usually embed the file name in the document id;
        // use it here so SlotManager sees a realistic adventure file name.
        val id = name
        val file = File(baseDir, "adventure-${System.nanoTime()}").apply { createNewFile() }
        nodes[id] = Node(id, name, "application/json", ROOT_ID, file)
        return docUri(id)
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val columns = projection ?: arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        val cursor = MatrixCursor(columns)
        val parentId = parentIdFromChildrenUri(uri)
        val children = if (parentId == null) {
            emptyList()
        } else {
            nodes.values.filter { it.parentId == parentId }
        }
        for (node in children) {
            val row = cursor.newRow()
            for (column in columns) {
                row.add(
                    when (column) {
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID -> node.id
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME -> node.name
                        DocumentsContract.Document.COLUMN_MIME_TYPE -> node.mime
                        else -> null
                    },
                )
            }
        }
        return cursor
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val node = requireNotNull(nodes[docIdOf(uri)]) { "unknown document: $uri" }
        val file = requireNotNull(node.file) { "directory cannot be opened: $uri" }
        val parsedMode = ParcelFileDescriptor.parseMode(mode)
        if (parsedMode and ParcelFileDescriptor.MODE_CREATE == 0 &&
            parsedMode and ParcelFileDescriptor.MODE_WRITE_ONLY != 0
        ) {
            file.createNewFile()
        }
        return ParcelFileDescriptor.open(file, parsedMode)
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        return when (method) {
            METHOD_CREATE_DOCUMENT -> createDocument(extras)
            METHOD_DELETE_DOCUMENT -> {
                val uri = extras?.getParcelable<Uri>(EXTRA_URI)
                if (uri != null) deleteDocument(uri)
                Bundle.EMPTY
            }
            else -> super.call(method, arg, extras)
        }
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        val node = nodes.remove(docIdOf(uri)) ?: return 0
        node.file?.delete()
        return 1
    }

    override fun getType(uri: Uri): String? = nodes[docIdOf(uri)]?.mime

    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw UnsupportedOperationException("insert is not used; DocumentsContract uses call()")

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    private fun createDocument(extras: Bundle?): Bundle {
        val parentUri = extras?.getParcelable<Uri>(EXTRA_URI)
            ?: throw IllegalArgumentException("missing parent uri")
        val mime = extras.getString(EXTRA_MIME_TYPE) ?: "application/octet-stream"
        val name = extras.getString(EXTRA_DISPLAY_NAME) ?: "unnamed"
        val parentId = docIdOf(parentUri)
        require(nodes[parentId] != null) { "unknown parent: $parentUri" }

        val id = newId()
        val file = if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
            File(baseDir, id).apply { mkdirs() }
        } else {
            File(baseDir, id).apply { createNewFile() }
        }
        nodes[id] = Node(id, name, mime, parentId, file)

        return Bundle().apply {
            putParcelable(EXTRA_URI, docUri(id))
        }
    }

    /**
     * DocumentsContract.deleteDocument calls a provider `call` with the
     * document URI but our `call` override cannot see it, so the actual
     * deletion is implemented through [delete], which the platform
     * resolver invokes for document deletion on some versions. This
     * helper keeps tests deterministic for the current SDK.
     */
    fun deleteDocument(uri: Uri): Boolean {
        val node = nodes.remove(docIdOf(uri)) ?: return false
        node.file?.delete()
        return true
    }

    private fun parentIdFromChildrenUri(uri: Uri): String? {
        val segments = uri.pathSegments
        if (segments.size < 2 || segments.last() != "children") return null
        return segments[segments.size - 2]
    }

    private fun docIdOf(uri: Uri): String = DocumentsContract.getDocumentId(uri)

    private fun docUri(id: String): Uri =
        DocumentsContract.buildDocumentUriUsingTree(treeUri, id)

    private fun newId(): String = "doc-${nextId++}"
}
