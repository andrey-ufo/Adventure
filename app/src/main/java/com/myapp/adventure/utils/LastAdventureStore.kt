package com.myapp.adventure.utils

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import androidx.core.net.toUri

/** Remembers the last adventure the user opened in the reader. */
object LastAdventureStore {

    private const val PREFS = "adventure_prefs"
    private const val KEY = "last_adventure_uri"

    fun get(context: Context): Uri? =
        prefs(context).getString(KEY, null)?.toUri()

    fun set(context: Context, uri: Uri?) {
        prefs(context).edit {
            if (uri == null) remove(KEY) else putString(KEY, uri.toString())
        }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
