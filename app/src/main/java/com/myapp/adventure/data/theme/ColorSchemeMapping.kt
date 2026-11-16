package com.myapp.adventure.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.myapp.adventure.data.theme.AppThemeConfig
import com.myapp.adventure.data.theme.ThemeColors

/**
 * Bridges the data-layer [AppThemeConfig] (Compose-free, colors as Long)
 * to Compose's [ColorScheme] / [AdventureExtendedColors].
 *
 * Kept in the UI layer on purpose: `data.theme` must not depend on Compose,
 * so the conversion lives here.
 */

/** Converts the light or dark palette of [this] to a Compose [ColorScheme]. */
fun AppThemeConfig.toColorScheme(isDark: Boolean): ColorScheme {
    val c = if (isDark) dark else light
    return if (isDark) {
        darkColorScheme().copyWithPalette(c)
    } else {
        lightColorScheme().copyWithPalette(c)
    }
}

/**
 * Copies every role from [c] onto the receiver. Roles that [ThemeColors]
 * does not define (surfaceContainer*, surfaceTint, …) keep their Material
 * default — extend [ThemeColors] later if a theme needs them.
 */
private fun ColorScheme.copyWithPalette(c: ThemeColors): ColorScheme = copy(
    primary = c.primary.toComposeColor(),
    onPrimary = c.onPrimary.toComposeColor(),
    primaryContainer = c.primaryContainer.toComposeColor(),
    onPrimaryContainer = c.onPrimaryContainer.toComposeColor(),
    inversePrimary = c.inversePrimary.toComposeColor(),

    secondary = c.secondary.toComposeColor(),
    onSecondary = c.onSecondary.toComposeColor(),
    secondaryContainer = c.secondaryContainer.toComposeColor(),
    onSecondaryContainer = c.onSecondaryContainer.toComposeColor(),

    tertiary = c.tertiary.toComposeColor(),
    onTertiary = c.onTertiary.toComposeColor(),
    tertiaryContainer = c.tertiaryContainer.toComposeColor(),
    onTertiaryContainer = c.onTertiaryContainer.toComposeColor(),

    background = c.background.toComposeColor(),
    onBackground = c.onBackground.toComposeColor(),
    surface = c.surface.toComposeColor(),
    onSurface = c.onSurface.toComposeColor(),
    surfaceVariant = c.surfaceVariant.toComposeColor(),
    onSurfaceVariant = c.onSurfaceVariant.toComposeColor(),
    inverseSurface = c.inverseSurface.toComposeColor(),
    inverseOnSurface = c.inverseOnSurface.toComposeColor(),

    error = c.error.toComposeColor(),
    onError = c.onError.toComposeColor(),
    errorContainer = c.errorContainer.toComposeColor(),
    onErrorContainer = c.onErrorContainer.toComposeColor(),

    outline = c.outline.toComposeColor(),
    outlineVariant = c.outlineVariant.toComposeColor(),
    scrim = c.scrim.toComposeColor(),
)

/** Converts the project-specific status colors (not part of M3) to Compose. */
fun AppThemeConfig.toExtendedColors(isDark: Boolean): AdventureExtendedColors {
    val c = if (isDark) dark else light
    return AdventureExtendedColors(
        statusSuccess = c.statusSuccess.toComposeColor(),
        statusWarning = c.statusWarning.toComposeColor(),
        statusDanger = c.statusDanger.toComposeColor(),
        statusInfo = c.statusInfo.toComposeColor(),
        statusNeutral = c.statusNeutral.toComposeColor(),
    )
}

/**
 * ARGB Long (`0xAARRGGBB`) → Compose [Color]. `toInt()` truncates the
 * high zero bytes; the result is a 32-bit ARGB int, which is exactly
 * what [Color] expects.
 */
internal fun Long.toComposeColor(): Color = Color(this.toInt())