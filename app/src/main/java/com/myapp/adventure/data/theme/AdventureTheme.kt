package com.myapp.adventure.ui.theme

import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myapp.adventure.data.theme.AppThemeConfig
import com.myapp.adventure.data.theme.BuiltInThemes
import com.myapp.adventure.utils.FontManager
import com.myapp.adventure.utils.FontScope
import com.myapp.adventure.utils.FontSettings
import com.myapp.adventure.utils.ThemeManager

/**
 * Root theme for all Compose screens. Reads the current [AppThemeConfig]
 * from [ThemeManager] and the three font scopes from [FontManager], then
 * applies the interface scope via [MaterialTheme].
 *
 * Font size is expressed as a base size in SP and is multiplied into
 * `LocalDensity.fontScale`, mirroring what Android does with SP values.
 * DP values (padding, corner radii) are not affected.
 *
 * The reader text and editor override the interface scope through
 * [FontScopeProvider]. That provider resets the scale from
 * [LocalAdventureBaseDensity], so a large interface font never leaks into
 * the reader or editor.
 *
 * When [ThemeManager] is not initialized (previews, early composition),
 * `currentThemeSync()` returns [BuiltInThemes.DEFAULT].
 */
@Composable
fun AdventureTheme(
    isDark: Boolean = isSystemInDarkTheme(),
    config: AppThemeConfig = ThemeManager.currentThemeSync(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current

    val colorScheme = remember(config, isDark) { config.toColorScheme(isDark) }
    val extendedColors = remember(config, isDark) { config.toExtendedColors(isDark) }
    val shapes = remember(config) { config.shapes.toShapes() }

    val fontSettings by FontManager.settings.collectAsStateWithLifecycle()
    val interfaceFontFamily = remember(fontSettings.ui.fontName) {
        FontManager.getFontFamily(context, fontSettings.ui.fontName)
    }
    val typography = remember(interfaceFontFamily) {
        Typography().withFontFamily(interfaceFontFamily)
    }

    // Scale SP values by the user's chosen interface size. DP values remain
    // unaffected because LocalDensity.density is unchanged. Subscribing to
    // the flow gives an instant preview without recreating the Activity.
    val baseDensity = LocalDensity.current
    val interfaceScale = fontSettings.ui.sizePx /
            FontManager.DEFAULT_FONT_SIZE_PX.toFloat() * config.typography.baseScale
    val scaledDensity = remember(baseDensity, interfaceScale) {
        Density(baseDensity.density, baseDensity.fontScale * interfaceScale)
    }

    CompositionLocalProvider(
        LocalDensity provides scaledDensity,
        // Bare `Text` calls have no explicit style; give them the interface
        // font as well, not only the styles from `MaterialTheme.typography`.
        LocalTextStyle provides typography.bodyMedium,
        LocalAdventureBaseDensity provides baseDensity,
        LocalAdventureFontSettings provides fontSettings,
        LocalAdventureThemeConfig provides config,
        LocalAdventureExtendedColors provides extendedColors,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = shapes,
            typography = typography,
            content = content,
        )
    }
}

/**
 * Applies one of the non-interface font scopes to its subtree.
 *
 * The density is rebuilt from [LocalAdventureBaseDensity] (the system
 * density captured by [AdventureTheme]) rather than from the current
 * density. This is what makes reader/editor sizes independent from the
 * interface size when nested inside the root theme.
 */
@Composable
fun FontScopeProvider(
    scope: FontScope,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val settings = LocalAdventureFontSettings.current
    val scopeSettings = settings[scope]
    val fontFamily = remember(scopeSettings.fontName) {
        FontManager.getFontFamily(context, scopeSettings.fontName)
    }
    val baseDensity = LocalAdventureBaseDensity.current ?: LocalDensity.current
    val config = LocalAdventureThemeConfig.current
    val scale = scopeSettings.sizePx /
            FontManager.DEFAULT_FONT_SIZE_PX.toFloat() * config.typography.baseScale
    val density = remember(baseDensity, scale) {
        Density(baseDensity.density, baseDensity.fontScale * scale)
    }
    val baseTypography = MaterialTheme.typography
    val scopedTypography = remember(baseTypography, fontFamily) {
        baseTypography.withFontFamily(fontFamily)
    }

    CompositionLocalProvider(
        LocalDensity provides density,
        LocalTextStyle provides scopedTypography.bodyMedium,
    ) {
        MaterialTheme(
            typography = scopedTypography,
            content = content,
        )
    }
}

/** Replaces [FontFamily] on every Typography style, keeping sizes/weights intact. */
private fun Typography.withFontFamily(family: FontFamily): Typography = Typography(
    displayLarge = displayLarge.copy(fontFamily = family),
    displayMedium = displayMedium.copy(fontFamily = family),
    displaySmall = displaySmall.copy(fontFamily = family),
    headlineLarge = headlineLarge.copy(fontFamily = family),
    headlineMedium = headlineMedium.copy(fontFamily = family),
    headlineSmall = headlineSmall.copy(fontFamily = family),
    titleLarge = titleLarge.copy(fontFamily = family),
    titleMedium = titleMedium.copy(fontFamily = family),
    titleSmall = titleSmall.copy(fontFamily = family),
    bodyLarge = bodyLarge.copy(fontFamily = family),
    bodyMedium = bodyMedium.copy(fontFamily = family),
    bodySmall = bodySmall.copy(fontFamily = family),
    labelLarge = labelLarge.copy(fontFamily = family),
    labelMedium = labelMedium.copy(fontFamily = family),
    labelSmall = labelSmall.copy(fontFamily = family),
)

// ---------------------------------------------------------------------------
// Extended colors (statuses not covered by Material 3's ColorScheme)
// ---------------------------------------------------------------------------

/** Status colours available on top of [MaterialTheme.colorScheme]. */
@Immutable
data class AdventureExtendedColors(
    val statusSuccess: Color,
    val statusWarning: Color,
    val statusDanger: Color,
    val statusInfo: Color,
    val statusNeutral: Color,
)

/**
 * System density before any user font scaling. [FontScopeProvider] uses it
 * to build independent reader/editor scales.
 */
val LocalAdventureBaseDensity = staticCompositionLocalOf<Density?> { null }

/** Current snapshot of all three font scopes, published by [AdventureTheme]. */
val LocalAdventureFontSettings = staticCompositionLocalOf { FontSettings.Default }

val LocalAdventureThemeConfig = staticCompositionLocalOf<AppThemeConfig> {
    BuiltInThemes.DEFAULT
}

val LocalAdventureExtendedColors = staticCompositionLocalOf<AdventureExtendedColors> {
    BuiltInThemes.DEFAULT.toExtendedColors(isDark = false)
}

/**
 * Accessor companion for [AdventureTheme]. Use:
 * ```
 * Text(color = AdventureTheme.extendedColors.statusSuccess)
 * ```
 */
object AdventureTheme {

    val config: AppThemeConfig
        @Composable @ReadOnlyComposable
        get() = LocalAdventureThemeConfig.current

    val extendedColors: AdventureExtendedColors
        @Composable @ReadOnlyComposable
        get() = LocalAdventureExtendedColors.current
}

// ---------------------------------------------------------------------------
// Preview — verify in Android Studio that light and dark look correct.
// ---------------------------------------------------------------------------

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AdventureThemePreview() {
    AdventureTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Primary", color = MaterialTheme.colorScheme.primary)
                Text("On Surface", color = MaterialTheme.colorScheme.onSurface)
                Text("Surface Variant", color = MaterialTheme.colorScheme.surfaceVariant)
                Text("Success", color = AdventureTheme.extendedColors.statusSuccess)
                Text("Warning", color = AdventureTheme.extendedColors.statusWarning)
                Text("Danger", color = AdventureTheme.extendedColors.statusDanger)
            }
        }
    }
}