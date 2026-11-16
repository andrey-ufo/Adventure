package com.myapp.adventure.data.theme

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppThemeConfigSerializationTest {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true   // forward-compat: future fields don't break old readers
        encodeDefaults = false     // keep JSON minimal
    }

    // A minimal but valid config, all colours opaque, other fields default.
    private fun sampleConfig(): AppThemeConfig {
        val colors = ThemeColors(
            primary = 0xFF5D4037L,
            onPrimary = 0xFFFFFFFFL,
            primaryContainer = 0xFFE8D5C4L,
            onPrimaryContainer = 0xFF3E2723L,
            secondary = 0xFF795548L,
            onSecondary = 0xFFFFFFFFL,
            secondaryContainer = 0xFFEFE0D0L,
            onSecondaryContainer = 0xFF2E1A0FL,
            tertiary = 0xFF8D6E63L,
            onTertiary = 0xFFFFFFFFL,
            tertiaryContainer = 0xFFF0E0D0L,
            onTertiaryContainer = 0xFF2E1A0FL,
            background = 0xFFFAF5EAL,
            onBackground = 0xFF1C1410L,
            surface = 0xFFFAF5EAL,
            onSurface = 0xFF1C1410L,
            surfaceVariant = 0xFFEFE5D5L,
            onSurfaceVariant = 0xFF5D4037L,
            inverseSurface = 0xFF33261EL,
            inverseOnSurface = 0xFFF5E6D3L,
            inversePrimary = 0xFFD7CCC8L,
            error = 0xFFB3261EL,
            onError = 0xFFFFFFFFL,
            errorContainer = 0xFFF9DEDCL,
            onErrorContainer = 0xFF410E0BL,
            outline = 0xFFA1887FL,
            outlineVariant = 0xFFD7CCC8L,
            scrim = 0xFF000000L,
            statusSuccess = 0xFF2E6F3AL,
            statusWarning = 0xFF8F5000L,
            statusDanger = 0xFFC62828L,
            statusInfo = 0xFF1565C0L,
            statusNeutral = 0xFF5D4037L,
        )
        return AppThemeConfig(
            id = "default",
            displayName = "Adventure",
            isBuiltIn = true,
            light = colors,
            dark = colors, // same for this test — roundtrip doesn't care
        )
    }

    @Test
    fun `roundtrip preserves all colour values`() {
        val original = sampleConfig()
        val encoded = json.encodeToString(AppThemeConfig.serializer(), original)
        val decoded = json.decodeFromString(AppThemeConfig.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `serialized colours are human-readable hex`() {
        val encoded = json.encodeToString(AppThemeConfig.serializer(), sampleConfig())
        assertTrue("JSON should contain #5D4037", encoded.contains("#5D4037"))
        assertTrue("JSON should contain #FAF5EA", encoded.contains("#FAF5EA"))
        // Opaque colours must NOT be prefixed with alpha
        assertTrue(
            "Opaque colour must not serialize as #FF-prefixed",
            !encoded.contains("#FF5D4037"),
        )
    }

    @Test
    fun `translucent colour roundtrips with ARGB format`() {
        val translucent = 0x80FF0000L // 50% red
        val cfg = sampleConfig().let {
            it.copy(
                light = it.light.copy(primary = translucent),
                dark = it.dark.copy(primary = translucent),
            )
        }
        val encoded = json.encodeToString(AppThemeConfig.serializer(), cfg)
        assertTrue("Translucent must serialize as 8-digit hex", encoded.contains("#80FF0000"))
        val decoded = json.decodeFromString(AppThemeConfig.serializer(), encoded)
        assertEquals(translucent, decoded.light.primary)
    }

    @Test
    fun `short hex is accepted on decode`() {
        val minimalJson = """
            {
              "id": "test",
              "displayName": "Test",
              "light": {
                "primary": "#112233", "onPrimary": "#FFFFFF",
                "primaryContainer": "#000000", "onPrimaryContainer": "#FFFFFF",
                "secondary": "#000000", "onSecondary": "#FFFFFF",
                "secondaryContainer": "#000000", "onSecondaryContainer": "#FFFFFF",
                "tertiary": "#000000", "onTertiary": "#FFFFFF",
                "tertiaryContainer": "#000000", "onTertiaryContainer": "#FFFFFF",
                "background": "#FFFFFF", "onBackground": "#000000",
                "surface": "#FFFFFF", "onSurface": "#000000",
                "surfaceVariant": "#EEEEEE", "onSurfaceVariant": "#000000",
                "inverseSurface": "#000000", "inverseOnSurface": "#FFFFFF",
                "inversePrimary": "#FFFFFF",
                "error": "#FF0000", "onError": "#FFFFFF",
                "errorContainer": "#FFDDDD", "onErrorContainer": "#000000",
                "outline": "#888888", "outlineVariant": "#CCCCCC",
                "scrim": "#000000",
                "statusSuccess": "#00AA00", "statusWarning": "#AA8800",
                "statusDanger": "#AA0000", "statusInfo": "#0000AA",
                "statusNeutral": "#666666"
              },
              "dark": {
                "primary": "#112233", "onPrimary": "#FFFFFF",
                "primaryContainer": "#000000", "onPrimaryContainer": "#FFFFFF",
                "secondary": "#000000", "onSecondary": "#FFFFFF",
                "secondaryContainer": "#000000", "onSecondaryContainer": "#FFFFFF",
                "tertiary": "#000000", "onTertiary": "#FFFFFF",
                "tertiaryContainer": "#000000", "onTertiaryContainer": "#FFFFFF",
                "background": "#FFFFFF", "onBackground": "#000000",
                "surface": "#FFFFFF", "onSurface": "#000000",
                "surfaceVariant": "#EEEEEE", "onSurfaceVariant": "#000000",
                "inverseSurface": "#000000", "inverseOnSurface": "#FFFFFF",
                "inversePrimary": "#FFFFFF",
                "error": "#FF0000", "onError": "#FFFFFF",
                "errorContainer": "#FFDDDD", "onErrorContainer": "#000000",
                "outline": "#888888", "outlineVariant": "#CCCCCC",
                "scrim": "#000000",
                "statusSuccess": "#00AA00", "statusWarning": "#AA8800",
                "statusDanger": "#AA0000", "statusInfo": "#0000AA",
                "statusNeutral": "#666666"
              }
            }
        """.trimIndent()
        val cfg = json.decodeFromString(AppThemeConfig.serializer(), minimalJson)
        assertEquals(0xFF112233L, cfg.light.primary)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `invalid id is rejected`() {
        sampleConfig().copy(id = "Default Theme!") // space and !
    }

    @Test(expected = IllegalArgumentException::class)
    fun `blank display name is rejected`() {
        sampleConfig().copy(displayName = "   ")
    }
}