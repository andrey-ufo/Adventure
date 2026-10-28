package com.myapp.adventure.utils

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.myapp.adventure.data.Adventure
import com.myapp.adventure.data.ProgressData

object ProgressManager {

    private val gson = Gson()
    private const val PROGRESS_FILE_NAME = "progress.json"

    fun loadProgress(context: Context, adventureFileUri: Uri): ProgressData? {
        val progressMap = readProgressMap(context) ?: return null
        val fileName = getFileNameFromUri(context, adventureFileUri) ?: return null
        val progress = progressMap[fileName]
        if (progress != null) {
            Logger.log(context, "Прогресс загружен для $fileName: глава ${progress.currentChapterIndex+1}, сцена '${progress.currentSceneId}'")
        } else {
            Logger.log(context, "Прогресс для $fileName не найден в progress.json")
        }
        return progress
    }

    fun saveProgress(context: Context, adventureFileUri: Uri, progress: ProgressData) {
        val progressMap = readProgressMap(context)?.toMutableMap() ?: mutableMapOf()
        val fileName = getFileNameFromUri(context, adventureFileUri) ?: return
        progressMap[fileName] = progress
        writeProgressMap(context, progressMap)
        Logger.log(context, "Прогресс сохранён для $fileName: глава ${progress.currentChapterIndex+1}, сцена '${progress.currentSceneId}', шаги ${progress.currentStepsDone}")
    }

    fun deleteProgress(context: Context, adventureFileUri: Uri) {
        val progressMap = readProgressMap(context)?.toMutableMap() ?: return
        val fileName = getFileNameFromUri(context, adventureFileUri) ?: return
        progressMap.remove(fileName)
        writeProgressMap(context, progressMap)
        Logger.log(context, "Прогресс удалён для $fileName")
    }

    fun loadAdventureJson(context: Context, adventureFileUri: Uri): Adventure? {
        return try {
            val inputStream = context.contentResolver.openInputStream(adventureFileUri)
            val json = inputStream?.bufferedReader()?.use { it.readText() }
            if (json != null) {
                val adventure = Gson().fromJson(json, Adventure::class.java)
                Logger.log(context, "JSON приключения загружен для ${adventureFileUri.lastPathSegment}")
                adventure
            } else {
                Logger.log(context, "Не удалось прочитать JSON приключения: ${adventureFileUri.lastPathSegment}")
                null
            }
        } catch (e: Exception) {
            Logger.log(context, "Ошибка загрузки JSON приключения ${adventureFileUri.lastPathSegment}: ${e.message}")
            null
        }
    }

    private fun readProgressMap(context: Context): Map<String, ProgressData>? {
        val fileUri = getProgressFileUri(context)
        if (fileUri == null) {
            Logger.log(context, "Файл progress.json не найден")
            return null
        }
        return try {
            val json = context.contentResolver.openInputStream(fileUri)?.bufferedReader()?.use { it.readText() }
            if (json.isNullOrBlank()) {
                Logger.log(context, "Файл progress.json пуст")
                emptyMap()
            } else {
                val type = object : TypeToken<Map<String, ProgressData>>() {}.type
                val map = gson.fromJson<Map<String, ProgressData>>(json, type)
                Logger.log(context, "Карта прогресса загружена, записей: ${map.size}")
                map
            }
        } catch (e: Exception) {
            Logger.log(context, "Ошибка чтения progress.json: ${e.message}")
            null
        }
    }

    private fun writeProgressMap(context: Context, map: Map<String, ProgressData>) {
        val fileUri = getOrCreateProgressFile(context)
        if (fileUri == null) {
            Logger.log(context, "Не удалось создать или получить progress.json")
            return
        }
        try {
            val json = gson.toJson(map)
            context.contentResolver.openOutputStream(fileUri, "wt")?.use { outputStream ->
                outputStream.write(json.toByteArray())
            }
            Logger.log(context, "Карта прогресса записана, записей: ${map.size}")
        } catch (e: Exception) {
            Logger.log(context, "Ошибка записи progress.json: ${e.message}")
        }
    }

    private fun getOrCreateProgressFile(context: Context): Uri? {
        val folderUri = getBaseFolderUri(context) ?: return null
        val existing = getProgressFileUri(context)
        if (existing != null) return existing

        val parentDocId = DocumentsContract.getTreeDocumentId(folderUri)
        val rootDocUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, parentDocId)
        return try {
            val createdUri = DocumentsContract.createDocument(
                context.contentResolver,
                rootDocUri,
                "application/json",
                PROGRESS_FILE_NAME
            )
            Logger.log(context, "Создан новый файл progress.json")
            createdUri
        } catch (e: Exception) {
            Logger.log(context, "Ошибка создания progress.json: ${e.message}")
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
                val name = it.getString(it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME))
                if (name == PROGRESS_FILE_NAME) {
                    val docId = it.getString(it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID))
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