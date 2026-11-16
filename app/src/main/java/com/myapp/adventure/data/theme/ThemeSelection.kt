package com.myapp.adventure.data.theme

import androidx.appcompat.app.AppCompatDelegate

/**
 * The user's active theme selection: which palette to show + which night
 * mode. Together with [customThemes] this fully determines the app's look.
 *
 * [nightMode] is an `AppCompatDelegate.MODE_NIGHT_*` constant (an `Int`).
 * Using the raw constant instead of a higher-level enum keeps this class
 * free of UI concerns and makes DataStore round-tripping trivial.
 */
data class ThemeSelection(
    val themeId: String = DEFAULT_THEME_ID,
    val nightMode: Int = DEFAULT_NIGHT_MODE,
) {
    companion object {
        const val DEFAULT_THEME_ID = "default"
        val DEFAULT_NIGHT_MODE = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        val Default = ThemeSelection()
    }
}