package com.myapp.adventure.data.theme

import kotlinx.serialization.Serializable

/**
 * Complete description of a theme: colour palette (light + dark), plus
 * typography, shapes and spacing. This is the single source of truth for
 * the app's look, both for View screens (via the XML mirror) and for
 * Compose (via `ThemeManager.getColorScheme`).
 *
 * @property id Stable, URL-safe, lowercase identifier. Built-in themes
 *   use `"default"`; user themes will use `"custom_<uuid>"`. Never
 *   displayed to the user.
 * @property displayName Human-readable name shown in the theme picker.
 * @property isBuiltIn `true` for shipped presets (read-only), `false` for
 *   user themes (editable, deletable).
 * @property light Colour palette used in light mode.
 * @property dark Colour palette used in dark mode.
 * @property typography Font family id + scale factor.
 * @property shapes Corner radii.
 * @property spacing Spacing scale.
 *
 * The `@Serializable` with `explicitNulls = false` and default values on
 * every field makes this forward-compatible: adding new fields in a future
 * app version will still parse old JSON, as long as the new field has a
 * default value.
 */
@Serializable
data class AppThemeConfig(
    val id: String,
    val displayName: String,
    val isBuiltIn: Boolean = false,
    val light: ThemeColors,
    val dark: ThemeColors,
    val typography: ThemeTypography = ThemeTypography.Default,
    val shapes: ThemeShapes = ThemeShapes.Default,
    val spacing: ThemeSpacing = ThemeSpacing.Default,
) {
    init {
        require(id.matches(Regex("[a-z0-9_]+"))) {
            "Theme id must match [a-z0-9_]+, was: $id"
        }
        require(displayName.isNotBlank()) { "displayName must not be blank" }
    }
}