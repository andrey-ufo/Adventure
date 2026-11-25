package com.myapp.adventure.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class FontSettingsTest {

    @Test
    fun emptyRawUsesPerScopeDefaults() {
        val settings = FontSettings.resolve(RawFontSettings())

        assertEquals("EuroScript", settings.reader.fontName)
        assertEquals(19, settings.reader.sizePx)
        assertEquals("Caveat", settings.ui.fontName)
        assertEquals(23, settings.ui.sizePx)
        assertEquals("Source Sans 3", settings.editor.fontName)
        assertEquals(18, settings.editor.sizePx)
        assertEquals(FontSettings.Default, settings)
    }

    @Test
    fun legacyValuesMigrateToAllScopes() {
        val settings = FontSettings.resolve(
            RawFontSettings(
                legacyFontName = "Caveat",
                legacySizePx = 22,
            )
        )

        FontScope.entries.forEach { scope ->
            assertEquals("Caveat", settings[scope].fontName)
            assertEquals(22, settings[scope].sizePx)
        }
    }

    @Test
    fun perScopeValuesOverrideLegacy() {
        val settings = FontSettings.resolve(
            RawFontSettings(
                legacyFontName = "Caveat",
                legacySizePx = 22,
                reader = StoredFontScope(fontName = "MarckScript", sizePx = 18),
                ui = StoredFontScope(fontName = "EuroScript"),
                editor = StoredFontScope(sizePx = 28),
            )
        )

        assertEquals("MarckScript", settings.reader.fontName)
        assertEquals(18, settings.reader.sizePx)
        assertEquals("EuroScript", settings.ui.fontName)
        assertEquals(22, settings.ui.sizePx)
        assertEquals("Caveat", settings.editor.fontName)
        assertEquals(28, settings.editor.sizePx)
    }

    @Test
    fun sizesAreClamped() {
        val settings = FontSettings.resolve(
            RawFontSettings(
                legacySizePx = 1000,
                reader = StoredFontScope(sizePx = 1),
                ui = StoredFontScope(sizePx = 99),
                editor = StoredFontScope(sizePx = 0),
            )
        )

        assertEquals(FontSettings.MIN_SIZE_PX, settings.reader.sizePx)
        assertEquals(FontSettings.MAX_SIZE_PX, settings.ui.sizePx)
        assertEquals(FontSettings.MIN_SIZE_PX, settings.editor.sizePx)
    }

    @Test
    fun legacyDefaultAliasIsTreatedAsUnset() {
        val settings = FontSettings.resolve(
            RawFontSettings(legacyFontName = "По умолчанию")
        )

        FontScope.entries.forEach { scope ->
            assertEquals(FontSettings.Default[scope], settings[scope])
        }
    }

    @Test
    fun migrateOldDefaultsUpgradesUntouchedValues() {
        val raw = RawFontSettings(
            legacyFontName = "Bad Script",
            legacySizePx = 16,
            reader = StoredFontScope("Bad Script", 16),
            ui = StoredFontScope("По умолчанию", 16),
            editor = StoredFontScope(sizePx = 16),
        )

        val migrated = FontSettings.migrateOldDefaults(raw)

        assertEquals(RawFontSettings(), migrated)
        assertEquals(FontSettings.Default, FontSettings.resolve(migrated))
    }

    @Test
    fun migrateOldDefaultsPreservesCustomValues() {
        val raw = RawFontSettings(
            legacyFontName = "Caveat",
            legacySizePx = 22,
            reader = StoredFontScope("MarckScript", 18),
            ui = StoredFontScope("Caveat", 23),
            editor = StoredFontScope("Source Sans 3", 18),
        )

        assertEquals(raw, FontSettings.migrateOldDefaults(raw))
    }

    @Test
    fun migrateOldDefaultsPreservesPartialCustomValues() {
        val raw = RawFontSettings(
            reader = StoredFontScope(fontName = "MarckScript"),
            ui = StoredFontScope(sizePx = 24),
            editor = StoredFontScope("Bad Script", 18),
        )

        val migrated = FontSettings.migrateOldDefaults(raw)
        val settings = FontSettings.resolve(migrated)

        assertEquals(raw, migrated)
        assertEquals("MarckScript", settings.reader.fontName)
        assertEquals(19, settings.reader.sizePx)
        assertEquals("Caveat", settings.ui.fontName)
        assertEquals(24, settings.ui.sizePx)
        assertEquals("Bad Script", settings.editor.fontName)
        assertEquals(18, settings.editor.sizePx)
    }

    @Test
    fun withUpdatesOnlyTargetScope() {
        val base = FontSettings.resolve(RawFontSettings())
        val updated = base.with(FontScope.EDITOR, fontName = "Caveat", sizePx = 24)

        assertEquals("Caveat", updated.editor.fontName)
        assertEquals(24, updated.editor.sizePx)
        assertEquals(base.reader, updated.reader)
        assertEquals(base.ui, updated.ui)
    }

    @Test
    fun withClampsSize() {
        val updated = FontSettings.Default.with(FontScope.READER, sizePx = 100)

        assertEquals(FontSettings.MAX_SIZE_PX, updated.reader.sizePx)
    }
}
