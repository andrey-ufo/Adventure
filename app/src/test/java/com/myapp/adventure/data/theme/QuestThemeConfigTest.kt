package com.myapp.adventure.data.theme

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class QuestThemeConfigTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Test
    fun `U13 example theme parses`() {
        val parsed = json.decodeFromString<QuestThemeConfig>(EXAMPLE_THEME)

        assertEquals(1, parsed.schemaVersion)
        assertEquals("author_old_book", parsed.id)
        assertEquals("Старая книга", parsed.displayName)
        assertEquals(0xFFFAF5EAL, parsed.light.background)
        assertEquals(0xFF1C1410L, parsed.light.text)
        assertEquals("serif", parsed.typography.fontFamilyId)
        assertEquals(1.05f, parsed.typography.baseScale)
        assertEquals("assets/paper.png", parsed.assets.backgroundImage)
    }

    private companion object {
        const val EXAMPLE_THEME = """
            {
              "schema_version": 1,
              "id": "author_old_book",
              "display_name": "Старая книга",
              "light": {
                "background": "#FAF5EA",
                "text": "#1C1410",
                "muted": "#5D4037",
                "accent": "#5D4037",
                "choice_background": "#EFE5D5",
                "choice_text": "#1C1410",
                "choice_border": "#A1887F",
                "hud_background": "#EFE5D5",
                "hud_text": "#1C1410",
                "divider": "#D7CCC8",
                "selection": "#5D4037",
                "warning": "#8F5000"
              },
              "dark": {
                "background": "#000000",
                "text": "#E8DBC8",
                "muted": "#B0A398",
                "accent": "#D4B896",
                "choice_background": "#1C1815",
                "choice_text": "#E8DBC8",
                "choice_border": "#8A7D70",
                "hud_background": "#1C1815",
                "hud_text": "#E8DBC8",
                "divider": "#3A342E",
                "selection": "#D4B896",
                "warning": "#F0B060"
              },
              "typography": {
                "font_family_id": "serif",
                "base_scale": 1.05,
                "line_height_scale": 1.10
              },
              "shapes": {
                "cornerSmall": 3,
                "cornerMedium": 6,
                "cornerLarge": 12
              },
              "spacing": {
                "xs": 4,
                "sm": 8,
                "md": 16,
                "lg": 24,
                "xl": 32
              },
              "assets": {
                "background_image": "assets/paper.png"
              }
            }
        """
    }
}
