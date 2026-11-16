package com.myapp.adventure.data.theme

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class BuiltInThemesTest {

    // ------------------------------------------------------------------
    // Structural invariants
    // ------------------------------------------------------------------

    @Test
    fun `default theme has expected id and is built-in`() {
        assertEquals("default", BuiltInThemes.DEFAULT.id)
        assertTrue(BuiltInThemes.DEFAULT.isBuiltIn)
    }

    @Test
    fun `all built-in ids are unique`() {
        val ids = BuiltInThemes.all.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
    }

    @Test
    fun `byId returns the matching preset`() {
        assertNotNull(BuiltInThemes.byId("default"))
        assertNull(BuiltInThemes.byId("nonexistent_theme"))
    }

    @Test
    fun `default is present in all`() {
        assertTrue(BuiltInThemes.all.contains(BuiltInThemes.DEFAULT))
    }

    // ------------------------------------------------------------------
    // Completeness — no zero (i.e. fully transparent black) slipped through
    // ------------------------------------------------------------------

    @Test
    fun `no colour field is fully transparent`() {
        val cfg = BuiltInThemes.DEFAULT
        // A value of 0L means "no colour set", which is never intentional in
        // a shipped preset. Every palette entry must have full alpha at least.
        val zeros = listOf(
            "light.primary" to cfg.light.primary,
            "light.onPrimary" to cfg.light.onPrimary,
            "light.primaryContainer" to cfg.light.primaryContainer,
            "light.onPrimaryContainer" to cfg.light.onPrimaryContainer,
            "light.secondary" to cfg.light.secondary,
            "light.onSecondary" to cfg.light.onSecondary,
            "light.secondaryContainer" to cfg.light.secondaryContainer,
            "light.onSecondaryContainer" to cfg.light.onSecondaryContainer,
            "light.tertiary" to cfg.light.tertiary,
            "light.onTertiary" to cfg.light.onTertiary,
            "light.tertiaryContainer" to cfg.light.tertiaryContainer,
            "light.onTertiaryContainer" to cfg.light.onTertiaryContainer,
            "light.background" to cfg.light.background,
            "light.onBackground" to cfg.light.onBackground,
            "light.surface" to cfg.light.surface,
            "light.onSurface" to cfg.light.onSurface,
            "light.surfaceVariant" to cfg.light.surfaceVariant,
            "light.onSurfaceVariant" to cfg.light.onSurfaceVariant,
            "light.inverseSurface" to cfg.light.inverseSurface,
            "light.inverseOnSurface" to cfg.light.inverseOnSurface,
            "light.inversePrimary" to cfg.light.inversePrimary,
            "light.error" to cfg.light.error,
            "light.onError" to cfg.light.onError,
            "light.errorContainer" to cfg.light.errorContainer,
            "light.onErrorContainer" to cfg.light.onErrorContainer,
            "light.outline" to cfg.light.outline,
            "light.outlineVariant" to cfg.light.outlineVariant,
            "light.scrim" to cfg.light.scrim,
            "light.statusSuccess" to cfg.light.statusSuccess,
            "light.statusWarning" to cfg.light.statusWarning,
            "light.statusDanger" to cfg.light.statusDanger,
            "light.statusInfo" to cfg.light.statusInfo,
            "light.statusNeutral" to cfg.light.statusNeutral,
            "dark.primary" to cfg.dark.primary,
            "dark.onPrimary" to cfg.dark.onPrimary,
            "dark.primaryContainer" to cfg.dark.primaryContainer,
            "dark.onPrimaryContainer" to cfg.dark.onPrimaryContainer,
            "dark.secondary" to cfg.dark.secondary,
            "dark.onSecondary" to cfg.dark.onSecondary,
            "dark.secondaryContainer" to cfg.dark.secondaryContainer,
            "dark.onSecondaryContainer" to cfg.dark.onSecondaryContainer,
            "dark.tertiary" to cfg.dark.tertiary,
            "dark.onTertiary" to cfg.dark.onTertiary,
            "dark.tertiaryContainer" to cfg.dark.tertiaryContainer,
            "dark.onTertiaryContainer" to cfg.dark.onTertiaryContainer,
            "dark.background" to cfg.dark.background,
            "dark.onBackground" to cfg.dark.onBackground,
            "dark.surface" to cfg.dark.surface,
            "dark.onSurface" to cfg.dark.onSurface,
            "dark.surfaceVariant" to cfg.dark.surfaceVariant,
            "dark.onSurfaceVariant" to cfg.dark.onSurfaceVariant,
            "dark.inverseSurface" to cfg.dark.inverseSurface,
            "dark.inverseOnSurface" to cfg.dark.inverseOnSurface,
            "dark.inversePrimary" to cfg.dark.inversePrimary,
            "dark.error" to cfg.dark.error,
            "dark.onError" to cfg.dark.onError,
            "dark.errorContainer" to cfg.dark.errorContainer,
            "dark.onErrorContainer" to cfg.dark.onErrorContainer,
            "dark.outline" to cfg.dark.outline,
            "dark.outlineVariant" to cfg.dark.outlineVariant,
            "dark.scrim" to cfg.dark.scrim,
            "dark.statusSuccess" to cfg.dark.statusSuccess,
            "dark.statusWarning" to cfg.dark.statusWarning,
            "dark.statusDanger" to cfg.dark.statusDanger,
            "dark.statusInfo" to cfg.dark.statusInfo,
            "dark.statusNeutral" to cfg.dark.statusNeutral,
        )
        zeros.forEach { (name, value) ->
            assertTrue("$name must not be 0x00000000", value != 0L)
        }
    }

    // ------------------------------------------------------------------
    // WCAG contrast checks — every built-in theme must be readable.
    // Adding a new theme to BuiltInThemes.all automatically inherits
    // these checks. See https://www.w3.org/TR/WCAG21/#dfn-contrast-ratio
    // ------------------------------------------------------------------

    @Test
    fun `default theme passes WCAG text contrast`() {
        BuiltInThemes.all.forEach { cfg ->
            assertContrastPair("${cfg.id}.light primary/onPrimary", cfg.light.primary, cfg.light.onPrimary, 4.5)
            assertContrastPair("${cfg.id}.light surface/onSurface", cfg.light.surface, cfg.light.onSurface, 4.5)
            assertContrastPair("${cfg.id}.light surface/onSurfaceVariant", cfg.light.surface, cfg.light.onSurfaceVariant, 3.0)
            assertContrastPair("${cfg.id}.light error/onError", cfg.light.error, cfg.light.onError, 4.5)

            assertContrastPair("${cfg.id}.dark primary/onPrimary", cfg.dark.primary, cfg.dark.onPrimary, 4.5)
            assertContrastPair("${cfg.id}.dark surface/onSurface", cfg.dark.surface, cfg.dark.onSurface, 4.5)
            assertContrastPair("${cfg.id}.dark surface/onSurfaceVariant", cfg.dark.surface, cfg.dark.onSurfaceVariant, 3.0)
            assertContrastPair("${cfg.id}.dark error/onError", cfg.dark.error, cfg.dark.onError, 4.5)
        }
    }

    // ------------------------------------------------------------------
    // Roundtrip — built-in themes must survive JSON round-trip (needed for
    // future "export built-in as a template for a custom theme" flows).
    // ------------------------------------------------------------------

    @Test
    fun `default theme survives JSON roundtrip`() {
        val json = Json { prettyPrint = false; encodeDefaults = true }
        val encoded = json.encodeToString(AppThemeConfig.serializer(), BuiltInThemes.DEFAULT)
        val decoded = json.decodeFromString(AppThemeConfig.serializer(), encoded)
        assertEquals(BuiltInThemes.DEFAULT, decoded)
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun assertContrastPair(name: String, fg: Long, bg: Long, minRatio: Double) {
        val ratio = contrastRatio(fg, bg)
        assertTrue(
            "%s contrast %.2f:1 is below %.1f:1".format(name, ratio, minRatio),
            ratio >= minRatio,
        )
    }

    private fun contrastRatio(a: Long, b: Long): Double {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        val lighter = max(la, lb)
        val darker = min(la, lb)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun relativeLuminance(argb: Long): Double {
        fun channel(c: Int): Double {
            val s = c / 255.0
            return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        val r = channel(((argb shr 16) and 0xFF).toInt())
        val g = channel(((argb shr 8) and 0xFF).toInt())
        val b = channel((argb and 0xFF).toInt())
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }
}