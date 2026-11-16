package com.myapp.adventure.data.theme

import kotlinx.serialization.Serializable

/**
 * Full Material 3 colour role set, plus project-specific status colours.
 *
 * Every value is an ARGB colour encoded as [Long] `0xAARRGGBB`. Use
 * [HexColorSerializer] for JSON round-tripping. Convert to Compose
 * `Color` in the UI layer (`Color(argb)`), never in this layer — the
 * data module must not depend on Compose.
 */
@Serializable
data class ThemeColors(
    // Primary
    @Serializable(with = HexColorSerializer::class) val primary: Long,
    @Serializable(with = HexColorSerializer::class) val onPrimary: Long,
    @Serializable(with = HexColorSerializer::class) val primaryContainer: Long,
    @Serializable(with = HexColorSerializer::class) val onPrimaryContainer: Long,

    // Secondary
    @Serializable(with = HexColorSerializer::class) val secondary: Long,
    @Serializable(with = HexColorSerializer::class) val onSecondary: Long,
    @Serializable(with = HexColorSerializer::class) val secondaryContainer: Long,
    @Serializable(with = HexColorSerializer::class) val onSecondaryContainer: Long,

    // Tertiary
    @Serializable(with = HexColorSerializer::class) val tertiary: Long,
    @Serializable(with = HexColorSerializer::class) val onTertiary: Long,
    @Serializable(with = HexColorSerializer::class) val tertiaryContainer: Long,
    @Serializable(with = HexColorSerializer::class) val onTertiaryContainer: Long,

    // Background / Surface
    @Serializable(with = HexColorSerializer::class) val background: Long,
    @Serializable(with = HexColorSerializer::class) val onBackground: Long,
    @Serializable(with = HexColorSerializer::class) val surface: Long,
    @Serializable(with = HexColorSerializer::class) val onSurface: Long,
    @Serializable(with = HexColorSerializer::class) val surfaceVariant: Long,
    @Serializable(with = HexColorSerializer::class) val onSurfaceVariant: Long,
    @Serializable(with = HexColorSerializer::class) val inverseSurface: Long,
    @Serializable(with = HexColorSerializer::class) val inverseOnSurface: Long,
    @Serializable(with = HexColorSerializer::class) val inversePrimary: Long,

    // Error
    @Serializable(with = HexColorSerializer::class) val error: Long,
    @Serializable(with = HexColorSerializer::class) val onError: Long,
    @Serializable(with = HexColorSerializer::class) val errorContainer: Long,
    @Serializable(with = HexColorSerializer::class) val onErrorContainer: Long,

    // Outline
    @Serializable(with = HexColorSerializer::class) val outline: Long,
    @Serializable(with = HexColorSerializer::class) val outlineVariant: Long,

    // Scrim
    @Serializable(with = HexColorSerializer::class) val scrim: Long,

    // Project-specific statuses (not part of M3, used in editor / stats)
    @Serializable(with = HexColorSerializer::class) val statusSuccess: Long,
    @Serializable(with = HexColorSerializer::class) val statusWarning: Long,
    @Serializable(with = HexColorSerializer::class) val statusDanger: Long,
    @Serializable(with = HexColorSerializer::class) val statusInfo: Long,
    @Serializable(with = HexColorSerializer::class) val statusNeutral: Long,
)