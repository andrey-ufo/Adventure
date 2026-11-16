package com.myapp.adventure.data.theme

import kotlinx.serialization.Serializable

/**
 * Corner radii for Material 3 shapes, in dp.
 * A future "sharp / notebook" theme might set these to 0; a "soft / cozy"
 * theme to 12–20. Defaults match M3 guidance.
 */
@Serializable
data class ThemeShapes(
    val cornerSmall: Float = 4f,
    val cornerMedium: Float = 8f,
    val cornerLarge: Float = 16f,
) {
    init {
        require(cornerSmall >= 0f) { "cornerSmall must be >= 0" }
        require(cornerMedium >= 0f) { "cornerMedium must be >= 0" }
        require(cornerLarge >= 0f) { "cornerLarge must be >= 0" }
    }

    companion object {
        val Default = ThemeShapes()
    }
}