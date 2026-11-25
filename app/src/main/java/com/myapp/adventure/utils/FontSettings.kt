package com.myapp.adventure.utils

/**
 * Independent font areas of the app.
 *
 *  - [READER]  — the adventure text itself (scene, death and ending text).
 *  - [UI]      — the app interface, including reader HUD, choices and menus.
 *  - [EDITOR]  — every screen of the adventure editor.
 */
enum class FontScope {
    READER,
    UI,
    EDITOR,
}

data class FontScopeSettings(
    val fontName: String,
    val sizePx: Int,
)

/**
 * Raw per-scope values as they are stored in preferences. `null` means
 * "not configured yet"; [FontSettings.resolve] then falls back to the
 * legacy single-scope values or to the defaults.
 */
data class StoredFontScope(
    val fontName: String? = null,
    val sizePx: Int? = null,
)

/**
 * Snapshot of everything read from preferences. Kept as a plain data class
 * so the migration/defaulting rules can be tested on the JVM without an
 * Android `Context`.
 */
data class RawFontSettings(
    val legacyFontName: String? = null,
    val legacySizePx: Int? = null,
    val reader: StoredFontScope = StoredFontScope(),
    val ui: StoredFontScope = StoredFontScope(),
    val editor: StoredFontScope = StoredFontScope(),
)

data class FontSettings(
    val reader: FontScopeSettings,
    val ui: FontScopeSettings,
    val editor: FontScopeSettings,
) {
    operator fun get(scope: FontScope): FontScopeSettings = when (scope) {
        FontScope.READER -> reader
        FontScope.UI -> ui
        FontScope.EDITOR -> editor
    }

    fun with(
        scope: FontScope,
        fontName: String? = null,
        sizePx: Int? = null,
    ): FontSettings {
        val current = get(scope)
        val updated = current.copy(
            fontName = fontName ?: current.fontName,
            sizePx = clamp(sizePx ?: current.sizePx),
        )
        return when (scope) {
            FontScope.READER -> copy(reader = updated)
            FontScope.UI -> copy(ui = updated)
            FontScope.EDITOR -> copy(editor = updated)
        }
    }

    companion object {
        const val DEFAULT_READER_FONT_NAME = "EuroScript"
        const val DEFAULT_READER_SIZE_PX = 19
        const val DEFAULT_UI_FONT_NAME = "Caveat"
        const val DEFAULT_UI_SIZE_PX = 23
        const val DEFAULT_EDITOR_FONT_NAME = "Source Sans 3"
        const val DEFAULT_EDITOR_SIZE_PX = 18

        /**
         * The pre-three-scope default. Used only by the one-time migration:
         * untouched old default values are upgraded to the new per-scope
         * defaults, while explicit user choices are preserved.
         */
        const val OLD_DEFAULT_FONT_NAME = "Bad Script"
        const val OLD_DEFAULT_SIZE_PX = 16

        const val MIN_SIZE_PX = 10
        const val MAX_SIZE_PX = 30

        val Default: FontSettings = resolve(RawFontSettings())

        /**
         * Resolves stored values into a complete snapshot.
         *
         * Priority:
         *  1. the per-scope value;
         *  2. the legacy single-scope value;
         *  3. the per-scope default.
         *
         * Sizes are always clamped to [MIN_SIZE_PX]..[MAX_SIZE_PX]. The
         * legacy alias "По умолчанию" is treated as "no value".
         */
        fun resolve(raw: RawFontSettings): FontSettings {
            val legacyFont = normalizeFont(raw.legacyFontName)
            val legacySize = raw.legacySizePx?.let(::clamp)

            fun resolveScope(
                stored: StoredFontScope,
                defaultFont: String,
                defaultSizePx: Int,
            ): FontScopeSettings = FontScopeSettings(
                fontName = normalizeFont(stored.fontName) ?: legacyFont ?: defaultFont,
                sizePx = clamp(stored.sizePx ?: legacySize ?: defaultSizePx),
            )

            return FontSettings(
                reader = resolveScope(
                    raw.reader,
                    DEFAULT_READER_FONT_NAME,
                    DEFAULT_READER_SIZE_PX,
                ),
                ui = resolveScope(
                    raw.ui,
                    DEFAULT_UI_FONT_NAME,
                    DEFAULT_UI_SIZE_PX,
                ),
                editor = resolveScope(
                    raw.editor,
                    DEFAULT_EDITOR_FONT_NAME,
                    DEFAULT_EDITOR_SIZE_PX,
                ),
            )
        }

        /**
         * One-time migration from the old single default (Bad Script/16)
         * to the new per-scope defaults. A scope is cleared only when both
         * its font and size still look like the old default; anything else
         * is an explicit user choice and is preserved.
         */
        fun migrateOldDefaults(raw: RawFontSettings): RawFontSettings {
            fun isOldDefaultFont(name: String?): Boolean =
                name == null || name == OLD_DEFAULT_FONT_NAME || name == "По умолчанию"

            fun isOldDefaultSize(sizePx: Int?): Boolean =
                sizePx == null || sizePx == OLD_DEFAULT_SIZE_PX

            fun migrateScope(stored: StoredFontScope): StoredFontScope =
                if (isOldDefaultFont(stored.fontName) && isOldDefaultSize(stored.sizePx)) {
                    StoredFontScope()
                } else {
                    stored
                }

            val legacyIsOldDefault =
                isOldDefaultFont(raw.legacyFontName) && isOldDefaultSize(raw.legacySizePx)

            return raw.copy(
                legacyFontName = if (legacyIsOldDefault) null else raw.legacyFontName,
                legacySizePx = if (legacyIsOldDefault) null else raw.legacySizePx,
                reader = migrateScope(raw.reader),
                ui = migrateScope(raw.ui),
                editor = migrateScope(raw.editor),
            )
        }

        fun clamp(sizePx: Int): Int = sizePx.coerceIn(MIN_SIZE_PX, MAX_SIZE_PX)

        private fun normalizeFont(name: String?): String? =
            name?.takeUnless { it.isBlank() || it == "По умолчанию" }
    }
}
