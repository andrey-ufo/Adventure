package com.myapp.adventure.data.theme

import kotlinx.serialization.Serializable

/**
 * Typography descriptor. Deliberately minimal for now:
 *  - [fontFamilyId] is a reference to an entry in `FontManager`, e.g.
 *    `"system"`, `"serif"`, `"custom_abc123"` (the id assigned when the
 *    user imports a .ttf).
 *  - [baseScale] multiplies every text size, so a user theme can make
 *    the whole app "larger" or "smaller" without redefining each style.
 *
 * Future: per-style overrides (`bodyLargeSizeSp`, etc.) if a theme needs
 * fine-grained typography control. Not needed yet.
 */
@Serializable
data class ThemeTypography(
    val fontFamilyId: String = "system",
    val baseScale: Float = 1.0f,
) {
    init {
        require(baseScale > 0f) { "baseScale must be > 0, was $baseScale" }
    }

    companion object {
        val Default = ThemeTypography()
    }
}