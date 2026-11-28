package com.myapp.adventure.data.theme

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * U13 contract for an author theme in `theme.json`.
 *
 * This is deliberately narrower than [AppThemeConfig]: a quest theme may
 * style the reader (and its editor preview), but not the app chrome.
 */
@Serializable
data class QuestThemeConfig(
    @SerialName("schema_version")
    val schemaVersion: Int = 1,
    val id: String,
    @SerialName("display_name")
    val displayName: String,
    val light: ReaderColors,
    val dark: ReaderColors,
    val typography: ReaderTypography = ReaderTypography.Default,
    val shapes: ThemeShapes = ThemeShapes.Default,
    val spacing: ThemeSpacing = ThemeSpacing.Default,
    val assets: ReaderAssets = ReaderAssets.Default,
)

@Serializable
data class ReaderColors(
    @Serializable(with = HexColorSerializer::class) val background: Long,
    @Serializable(with = HexColorSerializer::class) val text: Long,
    @Serializable(with = HexColorSerializer::class) val muted: Long,
    @Serializable(with = HexColorSerializer::class) val accent: Long,
    @SerialName("choice_background")
    @Serializable(with = HexColorSerializer::class)
    val choiceBackground: Long,
    @SerialName("choice_text")
    @Serializable(with = HexColorSerializer::class)
    val choiceText: Long,
    @SerialName("choice_border")
    @Serializable(with = HexColorSerializer::class)
    val choiceBorder: Long,
    @SerialName("hud_background")
    @Serializable(with = HexColorSerializer::class)
    val hudBackground: Long,
    @SerialName("hud_text")
    @Serializable(with = HexColorSerializer::class)
    val hudText: Long,
    @Serializable(with = HexColorSerializer::class) val divider: Long,
    @Serializable(with = HexColorSerializer::class) val selection: Long,
    @Serializable(with = HexColorSerializer::class) val warning: Long,
)

@Serializable
data class ReaderTypography(
    @SerialName("font_family_id")
    val fontFamilyId: String = "system",
    @SerialName("base_scale")
    val baseScale: Float = 1.0f,
    @SerialName("line_height_scale")
    val lineHeightScale: Float = 1.0f,
) {
    init {
        require(baseScale > 0f) { "baseScale must be > 0" }
        require(lineHeightScale > 0f) { "lineHeightScale must be > 0" }
    }

    companion object {
        val Default = ReaderTypography()
    }
}

@Serializable
data class ReaderAssets(
    @SerialName("background_image")
    val backgroundImage: String? = null,
) {
    companion object {
        val Default = ReaderAssets()
    }
}
