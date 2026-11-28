package com.myapp.adventure.projects

import android.content.Context
import androidx.core.content.edit
import com.google.gson.Gson
import com.myapp.adventure.data.EditorDraft
import com.myapp.adventure.utils.Logger

/** Per-project drafts; the legacy single draft remains for no-project edits. */
object ProjectDraftManager {

    private const val PREFS = "editor_project_drafts"
    private const val PREFIX = "draft:"

    private val gson = Gson()

    fun load(context: Context, projectId: String): EditorDraft? {
        val json = prefs(context).getString(PREFIX + projectId, null) ?: return null
        return try {
            gson.fromJson(json, EditorDraft::class.java)
        } catch (e: Exception) {
            Logger.log(context, "Project draft read failed: ${e.message}")
            clear(context, projectId)
            null
        }
    }

    fun save(context: Context, projectId: String, draft: EditorDraft) {
        prefs(context).edit {
            putString(PREFIX + projectId, gson.toJson(draft))
        }
    }

    fun clear(context: Context, projectId: String) {
        prefs(context).edit { remove(PREFIX + projectId) }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
