package com.myapp.adventure.utils

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import com.myapp.adventure.R

object AdventureImportHelper {

    fun importAdventure(context: Context, sourceUri: Uri): String? {
        return try {
            val prefs = context.getSharedPreferences("adventure_prefs", 0)
            if (prefs.getString("adventure_folder_uri", null) == null) {
                Toast.makeText(
                    context,
                    R.string.import_toast_folder_not_selected,
                    Toast.LENGTH_SHORT,
                ).show()
                return null
            }

            val sourceName = context.contentResolver.query(
                sourceUri, null, null, null, null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else "imported_adventure.json"
                } else "imported_adventure.json"
            } ?: "imported_adventure.json"

            val json = context.contentResolver.openInputStream(sourceUri)
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: run {
                    Toast.makeText(
                        context,
                        R.string.import_toast_create_failed,
                        Toast.LENGTH_SHORT,
                    ).show()
                    return null
                }

            val folderName = sourceName.substringBeforeLast('.', sourceName)
            val folder = QuestStorage.createQuestFolder(
                context = context,
                baseName = QuestStorage.ADVENTURES_DIR,
                desiredName = folderName,
                questJson = json,
            )
            if (folder == null) {
                Toast.makeText(
                    context,
                    R.string.import_toast_create_failed,
                    Toast.LENGTH_SHORT,
                ).show()
                return null
            }
            Logger.log(context, "Приключение импортировано: ${folder.name}/quest.json")
            Toast.makeText(
                context,
                context.getString(R.string.import_toast_success_fmt, folder.name),
                Toast.LENGTH_SHORT,
            ).show()
            folder.name
        } catch (e: Exception) {
            Logger.log(context, "Ошибка импорта: ${e.message}")
            Toast.makeText(
                context,
                context.getString(R.string.import_toast_error_fmt, e.message),
                Toast.LENGTH_LONG,
            ).show()
            null
        }
    }
}
