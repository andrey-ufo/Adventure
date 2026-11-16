package com.myapp.adventure.data.theme

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import okio.Path.Companion.toOkioPath

/**
 * Creates the app's theme [DataStore].
 *
 * Uses [PreferenceDataStoreFactory.createWithPath] with an Okio path. This
 * factory internally creates an [androidx.datastore.core.okio.OkioStorage],
 * which delegates file moves to Okio's `FileSystem.atomicMove`. On Windows
 * Okio uses `Files.move(..., ATOMIC_MOVE, REPLACE_EXISTING)`, so it can
 * replace an existing `*.preferences_pb` file — unlike the default
 * `FileStorage`, which relies on `File.renameTo` and fails on Windows
 * (issue b/203087070). This makes production and JVM unit tests behave
 * consistently across platforms.
 *
 * The file `theme_prefs.preferences_pb` lives in the app's private storage.
 * On first creation, keys `theme_id` and `night_mode` are migrated out of
 * the legacy `adventure_prefs` SharedPreferences file; after migration these
 * keys are removed from SharedPreferences, while unrelated keys
 * (`adventure_folder_uri`, `logging_enabled`) stay put.
 *
 * Custom themes are stored under the `custom_themes_json` key as a JSON
 * array — the same file, so all theme state lives in one place.
 */
object ThemeDataStore {

    private const val DATASTORE_FILE = "theme_prefs"

    /** Legacy SharedPreferences file used before the DataStore migration. */
    private const val LEGACY_PREFS_FILE = "adventure_prefs"

    /** SharedPreferences keys migrated into the DataStore exactly once. */
    private val MIGRATED_KEYS = setOf("theme_id", "night_mode")

    fun create(context: Context): DataStore<Preferences> {
        val file = context.applicationContext.preferencesDataStoreFile(DATASTORE_FILE)
        return PreferenceDataStoreFactory.createWithPath(
            migrations = listOf(
                SharedPreferencesMigration(
                    context = context.applicationContext,
                    sharedPreferencesName = LEGACY_PREFS_FILE,
                    keysToMigrate = MIGRATED_KEYS,
                )
            ),
            produceFile = { file.toOkioPath() },
        )
    }
}