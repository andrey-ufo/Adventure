package com.myapp.adventure.data.theme

/**
 * Built-in theme presets shipped with the app. Read-only at runtime.
 *
 * IMPORTANT — mirroring with XML resources:
 * Each built-in theme MUST be kept in sync with the matching `<style>` and
 * `@color/md_*` tokens in `res/values/colors.xml` and
 * `res/values-night/colors.xml`. The XML side is what View screens read
 * via `?attr/`; this Kotlin side is what Compose reads via
 * `ThemeManager.getColorScheme()`.
 *
 * When changing a built-in theme, update:
 *   - this file (data),
 *   - values/colors.xml (light tokens, same hex),
 *   - values-night/colors.xml (dark tokens, same hex).
 * Phase 4 (removing View screens) will let us drop the XML mirror.
 */
object BuiltInThemes {

    /**
     * "Old book / traveler's journal" (light) and "pure black" (dark).
     * Matches the palette proposed in the architecture discussion:
     * leather binding, aged paper, ink text; pure black surfaces for OLED.
     */
    val DEFAULT: AppThemeConfig = AppThemeConfig(
        id = "default",
        displayName = "Adventure",
        isBuiltIn = true,
        light = ThemeColors(
            // Primary — leather binding
            primary = 0xFF5D4037L,
            onPrimary = 0xFFFFFFFFL,
            primaryContainer = 0xFFE8D5C4L,
            onPrimaryContainer = 0xFF3E2723L,

            // Secondary — mid leather
            secondary = 0xFF795548L,
            onSecondary = 0xFFFFFFFFL,
            secondaryContainer = 0xFFEFE0D0L,
            onSecondaryContainer = 0xFF2E1A0FL,

            // Tertiary — faded leather
            tertiary = 0xFF8D6E63L,
            onTertiary = 0xFFFFFFFFL,
            tertiaryContainer = 0xFFF0E0D0L,
            onTertiaryContainer = 0xFF2E1A0FL,

            // Background / surface — aged paper
            background = 0xFFFAF5EAL,
            onBackground = 0xFF1C1410L,
            surface = 0xFFFAF5EAL,
            onSurface = 0xFF1C1410L,
            surfaceVariant = 0xFFEFE5D5L,
            onSurfaceVariant = 0xFF5D4037L,
            inverseSurface = 0xFF33261EL,
            inverseOnSurface = 0xFFF5E6D3L,
            inversePrimary = 0xFFD7CCC8L,

            // Error — red ink
            error = 0xFFB3261EL,
            onError = 0xFFFFFFFFL,
            errorContainer = 0xFFF9DEDCL,
            onErrorContainer = 0xFF410E0BL,

            // Outline
            outline = 0xFFA1887FL,
            outlineVariant = 0xFFD7CCC8L,

            // Scrim
            scrim = 0xFF000000L,

            // Status colours (WCAG AA on surface)
            statusSuccess = 0xFF2E6F3AL,
            statusWarning = 0xFF8F5000L,
            statusDanger = 0xFFC62828L,
            statusInfo = 0xFF1565C0L,
            statusNeutral = 0xFF5D4037L,
        ),
        dark = ThemeColors(
            // Primary
            primary = 0xFFD4B896L,
            onPrimary = 0xFF3E2723L,
            primaryContainer = 0xFF4A3B2EL,
            onPrimaryContainer = 0xFFF5E6D3L,

            // Secondary
            secondary = 0xFFC8B8A8L,
            onSecondary = 0xFF2E1A0FL,
            secondaryContainer = 0xFF3E3126L,
            onSecondaryContainer = 0xFFE8D5C4L,

            // Tertiary
            tertiary = 0xFFB0A090L,
            onTertiary = 0xFF2E1A0FL,
            tertiaryContainer = 0xFF3A2E24L,
            onTertiaryContainer = 0xFFE8D5C4L,

            // Background / surface — pure black as requested
            background = 0xFF000000L,
            onBackground = 0xFFE8DBC8L,
            surface = 0xFF000000L,
            onSurface = 0xFFE8DBC8L,
            surfaceVariant = 0xFF1C1815L,
            onSurfaceVariant = 0xFFB0A398L,
            inverseSurface = 0xFFE8DBC8L,
            inverseOnSurface = 0xFF1C1410L,
            inversePrimary = 0xFF5D4037L,

            // Error
            error = 0xFFF2B8B5L,
            onError = 0xFF601410L,
            errorContainer = 0xFF8C1D18L,
            onErrorContainer = 0xFFF9DEDCL,

            // Outline
            outline = 0xFF8A7D70L,
            outlineVariant = 0xFF3A342EL,

            // Scrim
            scrim = 0xFF000000L,

            // Status colours (WCAG AA on pure black)
            statusSuccess = 0xFF81C784L,
            statusWarning = 0xFFFFB74DL,
            statusDanger = 0xFFEF9A9AL,
            statusInfo = 0xFF64B5F6L,
            statusNeutral = 0xFFB0A398L,
        ),
    )

    /** All built-in presets. Grows one line at a time as new presets ship. */
    val all: List<AppThemeConfig> = listOf(DEFAULT)

    /** Convenience lookup by id; `null` if not a built-in. */
    fun byId(id: String): AppThemeConfig? = all.firstOrNull { it.id == id }
}