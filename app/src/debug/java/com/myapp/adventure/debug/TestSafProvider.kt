package com.myapp.adventure.debug

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
 * Debug-only SAF DocumentsProvider used by instrumented tests. This is
 * the on-device counterpart of the Robolectric `FakeSafProvider`, so the
 * real ContentResolver/DocumentsContract path is exercised.
 */
@Suppress("DEPRECATION")
class TestSafProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "com.myapp.adventure.debug.saf"
        const val ROOT_ID = "root"

        private const val EXTRA_URI = "uri"
        private const val EXTRA_MIME_TYPE = "mime_type"
        private const val EXTRA_DISPLAY_NAME = "_display_name"

        private const val METHOD_CREATE_DOCUMENT = "android:createDocument"
        private const val METHOD_DELETE_DOCUMENT = "android:deleteDocument"

        fun treeUri(): Uri = DocumentsContract.buildTreeDocumentUri(AUTHORITY, ROOT_ID)
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
    private lateinit var tree: Uri

    override fun onCreate(): Boolean {
        val context = requireNotNull(context)
        baseDir = File(context.cacheDir, "instrumented-saf").apply { mkdirs() }
        nodes[ROOT_ID] = Node(ROOT_ID, "root", DocumentsContract.Document.MIME_TYPE_DIR, null, null)
        tree = treeUri()
        return true
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
        val node = requireNotNull(nodes[DocumentsContract.getDocumentId(uri)])
        val file = requireNotNull(node.file)
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.parseMode(mode))
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        return when (method) {
            METHOD_CREATE_DOCUMENT -> createDocument(extras)
            METHOD_DELETE_DOCUMENT -> {
                extras?.getParcelable<Uri>(EXTRA_URI)?.let(::deleteDocument)
                Bundle.EMPTY
            }
            else -> super.call(method, arg, extras)
        }
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        if (deleteDocument(uri)) 1 else 0

    override fun getType(uri: Uri): String? =
        nodes[DocumentsContract.getDocumentId(uri)]?.mime

    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw UnsupportedOperationException("DocumentsContract.createDocument is used")

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    private fun createDocument(extras: Bundle?): Bundle {
        val parentUri = requireNotNull(extras?.getParcelable<Uri>(EXTRA_URI))
        val parentId = DocumentsContract.getDocumentId(parentUri)
        require(nodes[parentId] != null)
        val mime = extras.getString(EXTRA_MIME_TYPE) ?: "application/octet-stream"
        val name = extras.getString(EXTRA_DISPLAY_NAME) ?: "unnamed"
        val id = "doc-${nextId++}"
        val file = if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
            File(baseDir, id).apply { mkdirs() }
        } else {
            File(baseDir, id).apply { createNewFile() }
        }
        nodes[id] = Node(id, name, mime, parentId, file)
        return Bundle().apply {
            putParcelable(EXTRA_URI, DocumentsContract.buildDocumentUriUsingTree(tree, id))
        }
    }

    private fun deleteDocument(uri: Uri): Boolean {
        val node = nodes.remove(DocumentsContract.getDocumentId(uri)) ?: return false
        node.file?.delete()
        return true
    }

    private fun parentIdFromChildrenUri(uri: Uri): String? {
        val segments = uri.pathSegments
        if (segments.size < 2 || segments.last() != "children") return null
        return segments[segments.size - 2]
    }
}
