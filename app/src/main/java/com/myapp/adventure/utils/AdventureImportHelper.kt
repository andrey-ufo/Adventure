package com.myapp.adventure.utils

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.widget.Toast

object AdventureImportHelper {

    /**
     * Копирует выбранный JSON-файл в папку Adventures приложения.
     * Возвращает имя сохранённого файла или null при ошибке.
     */
    fun importAdventure(context: Context, sourceUri: Uri): String? {
        return try {
            val prefs = context.getSharedPreferences("adventure_prefs", 0)
            val folderUriString = prefs.getString("adventure_folder_uri", null)
            if (folderUriString == null) {
                Toast.makeText(context, "Папка не выбрана", Toast.LENGTH_SHORT).show()
                return null
            }
            val folderUri = Uri.parse(folderUriString)

            val sourceName = context.contentResolver.query(sourceUri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else "imported_adventure.json"
                } else "imported_adventure.json"
            } ?: "imported_adventure.json"

            val adventuresDirUri = DocumentsContract.buildDocumentUriUsingTree(
                folderUri,
                DocumentsContract.getTreeDocumentId(folderUri) + "/Adventures"
            )
            val destUri = DocumentsContract.createDocument(
                context.contentResolver,
                adventuresDirUri,
                "application/json",
                sourceName
            )
            if (destUri != null) {
                context.contentResolver.openInputStream(sourceUri)?.use { input ->
                    context.contentResolver.openOutputStream(destUri)?.use { output ->
                        input.copyTo(output)
                    }
                }
                Logger.log(context, "Приключение импортировано: $sourceName")
                Toast.makeText(context, "Приключение импортировано: $sourceName", Toast.LENGTH_SHORT).show()
                sourceName
            } else {
                Toast.makeText(context, "Не удалось создать файл", Toast.LENGTH_SHORT).show()
                null
            }
        } catch (e: Exception) {
            Logger.log(context, "Ошибка импорта: ${e.message}")
            Toast.makeText(context, "Ошибка импорта: ${e.message}", Toast.LENGTH_LONG).show()
            null
        }
    }
}