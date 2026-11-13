package com.myapp.adventure.utils

import android.content.Context
import com.google.gson.Gson
import com.myapp.adventure.data.EditorDraft
import com.myapp.adventure.data.EditorScene
import com.myapp.adventure.data.EditorTag
import com.myapp.adventure.data.EditorVarDecl
import com.myapp.adventure.data.deepCopy

object EditorDraftManager {

    private const val PREFS_NAME = "editor_draft_prefs"
    private const val KEY_DRAFT = "draft_json"

    private val gson = Gson()

    /**
     * Ф2.7: [variables] добавлен **без default** — все call-sites
     * обязаны явно передать список, чтобы переменные не терялись в
     * черновике молча. Обновление `AdventureEditorFragment` —
     * в шаге 2 Ф2.7.
     */
    fun saveDraft(
        context: Context,
        title: String,
        author: String,
        description: String,
        variables: List<EditorVarDecl>,
        initialStateTags: List<EditorTag>,
        scenes: List<EditorScene>,
    ) {
        try {
            val draft = EditorDraft(
                title = title,
                author = author,
                description = description,
                variables = variables.map { it.copy() },
                initialStateTags = initialStateTags.map { it.copy() },
                scenes = scenes.map { it.deepCopy() },
            )
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_DRAFT, gson.toJson(draft)).apply()
        } catch (e: Exception) {
            Logger.log(context, "Ошибка сохранения черновика: ${e.message}")
        }
    }

    fun loadDraft(context: Context): EditorDraft? {
        val json = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_DRAFT, null) ?: return null
        return try {
            gson.fromJson(json, EditorDraft::class.java)
        } catch (e: Exception) {
            Logger.log(context, "Ошибка чтения черновика: ${e.message}")
            clearDraft(context)
            null
        }
    }

    fun clearDraft(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().remove(KEY_DRAFT).apply()
    }
}