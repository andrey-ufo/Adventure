package com.myapp.adventure.data.theme

import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/**
 * Keys stored inside the theme DataStore.
 *
 * - [KEY_THEME_ID] — id of the active theme (`"default"`, `"custom_<uuid>"`, …).
 * - [KEY_NIGHT_MODE] — an `AppCompatDelegate.MODE_NIGHT_*` constant, stored
 *   as an `Int` (matching what AppCompat uses internally).
 * - [KEY_CUSTOM_THEMES] — a JSON array of user-created `AppThemeConfig` objects.
 */
internal object ThemePreferences {
    val KEY_THEME_ID = stringPreferencesKey("theme_id")
    val KEY_NIGHT_MODE = intPreferencesKey("night_mode")
    val KEY_CUSTOM_THEMES = stringPreferencesKey("custom_themes_json")
}