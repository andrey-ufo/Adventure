package com.myapp.adventure.utils

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.net.toUri

/**
 * Canonical quest folder layout:
 *
 * ```
 * <selected root>/
 *   Adventures/<quest>/quest.json
 *   Adventures/<quest>/theme.json
 *   Adventures/<quest>/assets/
 *   NewAdventures/<project>/quest.json
 * ```
 *
 * The quest identity is the folder document id, not `quest.json` and not
 * the display title. This keeps progress/slots separate when every quest
 * uses the same file name.
 */
object QuestStorage {

    const val ADVENTURES_DIR = "Adventures"
    const val NEW_ADVENTURES_DIR = "NewAdventures"
    const val QUEST_FILE = "quest.json"
    const val THEME_FILE = "theme.json"
    const val ASSETS_DIR = "assets"

    data class QuestFolder(
        val id: String,
        val name: String,
        val folderUri: Uri,
        val questFileUri: Uri,
        val themeFileUri: Uri?,
    )

    private data class Child(
        val name: String,
        val docId: String,
        val uri: Uri,
        val mimeType: String?,
    )

    fun baseDirUri(context: Context, name: String): Uri? {
        val tree = treeUri(context) ?: return null
        val rootId = DocumentsContract.getTreeDocumentId(tree)
        val uri = DocumentsContract.buildDocumentUriUsingTree(tree, "$rootId/$name")
        return if (canQuery(context, uri)) {
            uri
        } else {
            createDirectory(context, tree, rootId, name)
        }
    }

    fun listQuestFolders(context: Context, baseName: String): List<QuestFolder> {
        val tree = treeUri(context) ?: return emptyList()
        val base = baseDirUri(context, baseName) ?: return emptyList()
        return children(context, tree, base)
            .filter { it.mimeType == DocumentsContract.Document.MIME_TYPE_DIR }
            .mapNotNull { folder ->
                val quest = findChild(context, tree, folder.docId, QUEST_FILE)
                    ?: return@mapNotNull null
                val theme = findChild(context, tree, folder.docId, THEME_FILE)
                QuestFolder(
                    id = folder.docId,
                    name = folder.name,
                    folderUri = folder.uri,
                    questFileUri = quest.uri,
                    themeFileUri = theme?.uri,
                )
            }
            .sortedBy { it.name.lowercase() }
    }

    fun createQuestFolder(
        context: Context,
        baseName: String,
        desiredName: String,
        questJson: String,
        themeJson: String? = null,
    ): QuestFolder? {
        val tree = treeUri(context) ?: return null
        val base = baseDirUri(context, baseName) ?: return null
        val name = uniqueFolderName(context, tree, base, desiredName)
        val folder = createDirectory(
            context,
            tree,
            DocumentsContract.getDocumentId(base),
            name,
        ) ?: return null
        val folderDocId = DocumentsContract.getDocumentId(folder)
        val quest = createFile(context, tree, folderDocId, QUEST_FILE, questJson)
            ?: return null
        if (themeJson != null) {
            createFile(context, tree, folderDocId, THEME_FILE, themeJson)
        }
        return QuestFolder(
            id = folderDocId,
            name = name,
            folderUri = folder,
            questFileUri = quest,
            themeFileUri = findChild(context, tree, folderDocId, THEME_FILE)?.uri,
        )
    }

    fun copyQuestFolder(
        context: Context,
        source: QuestFolder,
        baseName: String,
        desiredName: String,
    ): QuestFolder? {
        val tree = treeUri(context) ?: return null
        val base = baseDirUri(context, baseName) ?: return null
        val name = uniqueFolderName(context, tree, base, desiredName)
        val target = createDirectory(
            context,
            tree,
            DocumentsContract.getDocumentId(base),
            name,
        ) ?: return null
        val targetId = DocumentsContract.getDocumentId(target)
        if (!copyChildren(context, tree, source.id, targetId)) return null
        val quest = findChild(context, tree, targetId, QUEST_FILE) ?: return null
        return QuestFolder(
            id = targetId,
            name = name,
            folderUri = target,
            questFileUri = quest.uri,
            themeFileUri = findChild(context, tree, targetId, THEME_FILE)?.uri,
        )
    }

    fun deleteQuestFolder(context: Context, questFileUri: Uri): Boolean {
        val tree = treeUri(context) ?: return false
        val parentId = parentDocumentId(questFileUri) ?: return false
        val parentName = displayNameOfId(parentId)
        if (parentName == ADVENTURES_DIR || parentName == NEW_ADVENTURES_DIR) {
            return false
        }
        val folder = DocumentsContract.buildDocumentUriUsingTree(tree, parentId)
        return deleteRecursively(context, tree, folder)
    }

    /** Stable key for progress/slots. Flat legacy files fall back to name. */
    fun questIdForFile(context: Context, fileUri: Uri): String? {
        val parentId = parentDocumentId(fileUri) ?: return null
        val parentName = displayNameOfId(parentId)
        if (parentName == ADVENTURES_DIR || parentName == NEW_ADVENTURES_DIR) {
            return fileName(fileUri)
        }
        return parentId
    }

    fun questFolderForFile(context: Context, fileUri: Uri): Uri? {
        val tree = treeUri(context) ?: return null
        val parentId = parentDocumentId(fileUri) ?: return null
        return DocumentsContract.buildDocumentUriUsingTree(tree, parentId)
    }

    fun readQuestJson(context: Context, questFileUri: Uri): String? =
        runCatching {
            context.contentResolver.openInputStream(questFileUri)
                ?.bufferedReader()
                ?.use { it.readText() }
        }.getOrNull()

    fun writeQuestJson(context: Context, questFileUri: Uri, json: String): Boolean =
        runCatching {
            context.contentResolver.openOutputStream(questFileUri, "wt")?.use {
                it.write(json.toByteArray(Charsets.UTF_8))
            } != null
        }.getOrDefault(false)

    // -------------------- SAF internals --------------------

    private fun treeUri(context: Context): Uri? =
        context.getSharedPreferences("adventure_prefs", Context.MODE_PRIVATE)
            .getString("adventure_folder_uri", null)
            ?.toUri()

    private fun parentDocumentId(uri: Uri): String? = runCatching {
        DocumentsContract.getDocumentId(uri).substringBeforeLast('/', "")
    }.getOrNull()?.takeIf { it.isNotBlank() }

    private fun fileName(uri: Uri): String? = runCatching {
        DocumentsContract.getDocumentId(uri)
            .substringAfterLast("/")
            .substringAfterLast(":")
    }.getOrNull()?.takeIf { it.isNotBlank() }

    private fun displayNameOfId(docId: String): String =
        docId.substringAfterLast("/").substringAfterLast(":")

    private fun canQuery(context: Context, docUri: Uri): Boolean = runCatching {
        context.contentResolver.query(
            DocumentsContract.buildChildDocumentsUriUsingTree(
                docUri,
                DocumentsContract.getDocumentId(docUri),
            ),
            null,
            null,
            null,
            null,
        )?.use { true } ?: false
    }.getOrDefault(false)

    private fun children(context: Context, tree: Uri, parent: Uri): List<Child> {
        val parentId = DocumentsContract.getDocumentId(parent)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        val result = mutableListOf<Child>()
        context.contentResolver.query(childrenUri, null, null, null, null)?.use { cursor ->
            val id = cursor.getColumnIndexOrThrow(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            )
            val name = cursor.getColumnIndexOrThrow(
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            )
            val mime = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
            while (cursor.moveToNext()) {
                val docId = cursor.getString(id) ?: continue
                val display = cursor.getString(name) ?: continue
                result += Child(
                    name = display,
                    docId = docId,
                    uri = DocumentsContract.buildDocumentUriUsingTree(tree, docId),
                    mimeType = if (mime >= 0) cursor.getString(mime) else null,
                )
            }
        }
        return result
    }

    private fun findChild(
        context: Context,
        tree: Uri,
        parentId: String,
        name: String,
    ): Child? {
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, parentId)
        return children(context, tree, parent).firstOrNull { it.name == name }
    }

    private fun createDirectory(
        context: Context,
        tree: Uri,
        parentId: String,
        name: String,
    ): Uri? = runCatching {
        DocumentsContract.createDocument(
            context.contentResolver,
            DocumentsContract.buildDocumentUriUsingTree(tree, parentId),
            DocumentsContract.Document.MIME_TYPE_DIR,
            name,
        )
    }.getOrNull()

    private fun createFile(
        context: Context,
        tree: Uri,
        parentId: String,
        name: String,
        content: String,
    ): Uri? {
        return try {
            val uri = DocumentsContract.createDocument(
                context.contentResolver,
                DocumentsContract.buildDocumentUriUsingTree(tree, parentId),
                "application/json",
                name,
            ) ?: return null
            context.contentResolver.openOutputStream(uri, "wt")?.use {
                it.write(content.toByteArray(Charsets.UTF_8))
            } ?: return null
            uri
        } catch (e: Exception) {
            Logger.log(context, "Quest file create failed: ${e.message}")
            null
        }
    }

    private fun copyChildren(
        context: Context,
        tree: Uri,
        sourceId: String,
        targetId: String,
    ): Boolean {
        val source = DocumentsContract.buildDocumentUriUsingTree(tree, sourceId)
        return children(context, tree, source).all { child ->
            if (child.mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                val created = createDirectory(context, tree, targetId, child.name)
                    ?: return@all false
                val targetChildId = DocumentsContract.getDocumentId(created)
                copyChildren(context, tree, child.docId, targetChildId)
            } else {
                runCatching {
                    val target = DocumentsContract.createDocument(
                        context.contentResolver,
                        DocumentsContract.buildDocumentUriUsingTree(tree, targetId),
                        child.mimeType ?: "application/octet-stream",
                        child.name,
                    ) ?: return@all false
                    context.contentResolver.openInputStream(child.uri)?.use { input ->
                        context.contentResolver.openOutputStream(target)?.use { output ->
                            input.copyTo(output)
                        }
                    } ?: return@all false
                    true
                }.getOrDefault(false)
            }
        }
    }

    private fun deleteRecursively(context: Context, tree: Uri, folder: Uri): Boolean {
        val folderId = DocumentsContract.getDocumentId(folder)
        for (child in children(context, tree, folder)) {
            if (child.mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                if (!deleteRecursively(context, tree, child.uri)) return false
            } else {
                if (!runCatching {
                        DocumentsContract.deleteDocument(
                            context.contentResolver,
                            child.uri,
                        )
                    }.getOrDefault(false)
                ) {
                    return false
                }
            }
        }
        return runCatching {
            DocumentsContract.deleteDocument(
                context.contentResolver,
                DocumentsContract.buildDocumentUriUsingTree(tree, folderId),
            )
        }.getOrDefault(false)
    }

    private fun uniqueFolderName(
        context: Context,
        tree: Uri,
        base: Uri,
        desired: String,
    ): String {
        val existing = children(context, tree, base).map { it.name.lowercase() }.toSet()
        val slug = desired
            .replace(Regex("""[\\/:*?"<>|]"""), "_")
            .trim()
            .trimEnd('.')
            .take(80)
            .ifBlank { "quest" }
        var name = slug
        var index = 2
        while (name.lowercase() in existing) {
            name = "$slug ($index)"
            index++
        }
        return name
    }
}
