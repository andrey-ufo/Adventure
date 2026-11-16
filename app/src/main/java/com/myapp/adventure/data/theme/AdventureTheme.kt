package com.myapp.adventure.ui.theme

import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
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
import com.myapp.adventure.data.theme.AppThemeConfig
import com.myapp.adventure.data.theme.BuiltInThemes
import com.myapp.adventure.utils.FontManager
import com.myapp.adventure.utils.ThemeManager

/**
 * Root theme for all Compose screens. Reads the current [AppThemeConfig]
 * from [ThemeManager] and the selected font from [FontManager], then
 * applies them via [MaterialTheme].
 *
 * Font scaling: the user's chosen scale (`FontManager.getFontScale`,
 * 0.85–1.3) is multiplied into `LocalDensity.fontScale`, mirroring what
 * Android does with SP values in the View world. DP values (padding,
 * corner radii) are not affected — same as on View screens.
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

    val fontFamily = remember { FontManager.getFontFamily(context) }
    val typography = remember(fontFamily) { Typography().withFontFamily(fontFamily) }

    // Scale SP values by the user's chosen font scale, like the View world does.
    // DP values remain unaffected because LocalDensity.density is unchanged.
    val baseDensity = LocalDensity.current
    val userFontScale = remember(config.typography.baseScale) {
        FontManager.getFontScale(context) * config.typography.baseScale
    }
    val scaledDensity = remember(baseDensity, userFontScale) {
        Density(baseDensity.density, baseDensity.fontScale * userFontScale)
    }

    CompositionLocalProvider(
        LocalDensity provides scaledDensity,
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