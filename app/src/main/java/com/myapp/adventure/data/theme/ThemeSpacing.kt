package com.myapp.adventure.data.theme

import kotlinx.serialization.Serializable

/**
 * Spacing scale in dp. Screens use semantic names, not raw numbers, so a
 * theme can adjust density (e.g. a "compact" reading theme) globally.
 */
@Serializable
data class ThemeSpacing(
    val xs: Float = 4f,
    val sm: Float = 8f,
    val md: Float = 16f,
    val lg: Float = 24f,
    val xl: Float = 32f,
) {
    init {
        require(xs >= 0f && sm >= 0f && md >= 0f && lg >= 0f && xl >= 0f) {
            "Spacing values must be >= 0"
        }
    }

    companion object {
        val Default = ThemeSpacing()
    }
}