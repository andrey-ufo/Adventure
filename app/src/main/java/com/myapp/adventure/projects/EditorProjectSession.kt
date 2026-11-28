package com.myapp.adventure.projects

import android.content.Context
import androidx.core.content.edit
import com.google.gson.Gson

/** The file currently bound to the editor; survives process recreation. */
object EditorProjectSession {

    private const val PREFS = "editor_project_session"
    private const val KEY = "current_project"

    private val gson = Gson()

    fun current(context: Context): ProjectRef? {
        val json = prefs(context).getString(KEY, null) ?: return null
        return runCatching { gson.fromJson(json, ProjectRef::class.java) }.getOrNull()
    }

    fun setCurrent(context: Context, project: ProjectRef) {
        prefs(context).edit { putString(KEY, gson.toJson(project)) }
    }

    fun clear(context: Context) {
        prefs(context).edit { remove(KEY) }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
