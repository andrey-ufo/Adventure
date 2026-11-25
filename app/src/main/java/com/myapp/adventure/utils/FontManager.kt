package com.myapp.adventure.utils

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import androidx.compose.ui.text.font.FontFamily
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt
import java.io.File
import java.io.FileOutputStream

/**
 * Owns font preferences for the three independent areas of the app
 * (reader text, app interface and editor).
 *
 * Reading and migration are done once by [initialize]; afterwards the
 * current snapshot is exposed through [settings] so the Compose theme can
 * react immediately without recreating the Activity.
 */
object FontManager {

    private const val PREFS_NAME = "font_prefs"
    private const val KEY_LEGACY_SELECTED_FONT = "selected_font"
    private const val KEY_LEGACY_FONT_SIZE_PX = "font_size_px"
    private const val KEY_LEGACY_FONT_SCALE = "font_scale"
    private const val KEY_CUSTOM_FONTS = "custom_fonts"
    private const val KEY_FONT_SETTINGS_VERSION = "font_settings_version"
    private const val KEY_FONT_PREFIX = "font_name_"
    private const val KEY_SIZE_PREFIX = "font_size_"
    private const val FONTS_DIR = "fonts"

    /** Version of the per-scope font defaults. Bump when they change. */
    private const val CURRENT_FONT_SETTINGS_VERSION = 2

    private const val DEFAULT_FONT = FontSettings.OLD_DEFAULT_FONT_NAME

    /** Scale anchor: a slider value of 16 means 1.0×. */
    const val DEFAULT_FONT_SIZE_PX = FontSettings.OLD_DEFAULT_SIZE_PX
    const val MIN_FONT_SIZE_PX = FontSettings.MIN_SIZE_PX
    const val MAX_FONT_SIZE_PX = FontSettings.MAX_SIZE_PX

    private val _settings = MutableStateFlow(FontSettings.Default)
    val settings: StateFlow<FontSettings> = _settings.asStateFlow()

    @Volatile
    private var initialized = false

    private val typefaceCache = mutableMapOf<String, Typeface>()
    private val fontFamilyCache = mutableMapOf<String, FontFamily>()

    /**
     * Reads, migrates and publishes the current font settings. Safe to call
     * repeatedly; `MainActivity` calls it once before the first composition.
     */
    @Synchronized
    fun initialize(context: Context) {
        val appContext = context.applicationContext
        val prefs = prefs(appContext)
        val resolved = readSettingsFromPrefs(prefs)
        persistResolved(prefs, resolved)
        _settings.value = resolved
        initialized = true
    }

    fun getSettings(context: Context): FontSettings {
        if (!initialized) initialize(context)
        return _settings.value
    }

    fun getSelectedFont(context: Context, scope: FontScope): String =
        getSettings(context)[scope].fontName

    fun getFontSizePx(context: Context, scope: FontScope): Int =
        getSettings(context)[scope].sizePx

    fun setSelectedFont(context: Context, scope: FontScope, fontName: String) {
        val appContext = context.applicationContext
        if (!initialized) initialize(appContext)
        val normalized = fontName.takeUnless { it.isBlank() } ?: DEFAULT_FONT
        prefs(appContext).edit()
            .putString(fontKey(scope), normalized)
            .apply()
        _settings.value = _settings.value.with(scope, fontName = normalized)
        Logger.log(appContext, "Выбран шрифт ${scope.name}: $normalized")
    }

    fun setFontSizePx(context: Context, scope: FontScope, sizePx: Int) {
        val appContext = context.applicationContext
        if (!initialized) initialize(appContext)
        val clamped = FontSettings.clamp(sizePx)
        prefs(appContext).edit()
            .putInt(sizeKey(scope), clamped)
            .apply()
        _settings.value = _settings.value.with(scope, sizePx = clamped)
        Logger.log(appContext, "Размер шрифта ${scope.name}: $clamped")
    }

    /** Legacy compatibility: the old single setting lived in the UI scope. */
    fun getSelectedFont(context: Context): String = getSelectedFont(context, FontScope.UI)

    /** Legacy compatibility: the old single setting lived in the UI scope. */
    fun getFontSizePx(context: Context): Int = getFontSizePx(context, FontScope.UI)

    /** Legacy compatibility: the old single setting lived in the UI scope. */
    fun setSelectedFont(context: Context, fontName: String) =
        setSelectedFont(context, FontScope.UI, fontName)

    /** Legacy compatibility: the old single setting lived in the UI scope. */
    fun setFontSizePx(context: Context, sizePx: Int) =
        setFontSizePx(context, FontScope.UI, sizePx)

    /** Множитель для совместимости с масштабированием текста в View-дереве. */
    fun getFontScale(context: Context): Float =
        getFontSizePx(context) / DEFAULT_FONT_SIZE_PX.toFloat()

    fun getAvailableFonts(context: Context): List<String> {
        val fonts = linkedSetOf(
            DEFAULT_FONT,
            "Caveat",
            "AmazHand_First",
            "EuroScript",
            "MarckScript",
            "Rubik Dirt",
            "Source Sans 3",
        )
        val prefs = prefs(context.applicationContext)
        fonts.addAll(prefs.getStringSet(KEY_CUSTOM_FONTS, emptySet()) ?: emptySet())
        return fonts.toList()
    }

    @Synchronized
    fun addFont(context: Context, sourceUri: Uri, fileName: String) {
        val appContext = context.applicationContext
        val fontsDir = File(appContext.filesDir, FONTS_DIR)
        if (!fontsDir.exists()) fontsDir.mkdirs()
        val destFile = File(fontsDir, fileName)
        appContext.contentResolver.openInputStream(sourceUri)?.use { input ->
            FileOutputStream(destFile).use { output -> input.copyTo(output) }
        }
        val prefs = prefs(appContext)
        val customFonts = prefs.getStringSet(KEY_CUSTOM_FONTS, emptySet())?.toMutableSet()
            ?: mutableSetOf()
        customFonts.add(fileName)
        prefs.edit().putStringSet(KEY_CUSTOM_FONTS, customFonts).apply()
        typefaceCache.remove(fileName)
        fontFamilyCache.remove(fileName)
        Logger.log(appContext, "Шрифт добавлен: $fileName")
    }

    @Synchronized
    fun getTypeface(context: Context, fontName: String): Typeface =
        typefaceCache.getOrPut(fontName) { loadTypeface(context, fontName) }

    private fun loadTypeface(context: Context, fontName: String): Typeface = try {
        when (fontName) {
            "По умолчанию",
            DEFAULT_FONT,
            -> Typeface.createFromAsset(context.assets, "fonts/BadScript-Regular.ttf")

            "Caveat" -> Typeface.createFromAsset(context.assets, "fonts/Caveat-Regular.ttf")
            "AmazHand_First" -> Typeface.createFromAsset(context.assets, "fonts/AmazHand_First.ttf")
            "EuroScript" -> Typeface.createFromAsset(context.assets, "fonts/EuroScript.ttf")
            "MarckScript" -> Typeface.createFromAsset(context.assets, "fonts/MarckScript-Regular.ttf")
            "Rubik Dirt" -> Typeface.createFromAsset(context.assets, "fonts/RubikDirt-Regular.ttf")
            "Source Sans 3" -> Typeface.createFromAsset(context.assets, "fonts/SourceSans3-Regular.ttf")

            else -> {
                val fontFile = File(context.filesDir, "$FONTS_DIR/$fontName")
                if (fontFile.exists()) Typeface.createFromFile(fontFile) else Typeface.DEFAULT
            }
        }
    } catch (e: Exception) {
        Logger.log(context, "Не удалось загрузить шрифт $fontName: ${e.message}")
        Typeface.DEFAULT
    }

    /**
     * Returns the requested font as a Compose [FontFamily].
     * - system/default → [FontFamily.Default];
     * - bundled / custom .ttf → wrapped over the resolved [Typeface].
     *
     * Font *size* is applied separately by the Compose theme via
     * `LocalDensity.fontScale`; this method concerns only the typeface.
     */
    @Synchronized
    fun getFontFamily(context: Context, fontName: String): FontFamily =
        fontFamilyCache.getOrPut(fontName) {
            val typeface = getTypeface(context, fontName)
            if (typeface == Typeface.DEFAULT) FontFamily.Default else FontFamily(typeface)
        }

    fun getFontFamily(context: Context, scope: FontScope): FontFamily =
        getFontFamily(context, getSelectedFont(context, scope))

    /** Legacy compatibility: the old single setting lived in the UI scope. */
    fun getFontFamily(context: Context): FontFamily =
        getFontFamily(context, FontScope.UI)

    // ------------------------------------------------------------------
    // Internal
    // ------------------------------------------------------------------

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun fontKey(scope: FontScope) = "$KEY_FONT_PREFIX${scope.name.lowercase()}"

    private fun sizeKey(scope: FontScope) = "$KEY_SIZE_PREFIX${scope.name.lowercase()}"

    private fun readSettingsFromPrefs(prefs: android.content.SharedPreferences): FontSettings {
        val legacyFont = prefs.getString(KEY_LEGACY_SELECTED_FONT, null)
        val legacySizePx = when {
            prefs.contains(KEY_LEGACY_FONT_SIZE_PX) ->
                prefs.getInt(KEY_LEGACY_FONT_SIZE_PX, DEFAULT_FONT_SIZE_PX)

            prefs.contains(KEY_LEGACY_FONT_SCALE) ->
                (prefs.getFloat(KEY_LEGACY_FONT_SCALE, 1f) * DEFAULT_FONT_SIZE_PX).roundToInt()

            else -> null
        }

        fun stored(scope: FontScope) = StoredFontScope(
            fontName = prefs.getString(fontKey(scope), null),
            sizePx = if (prefs.contains(sizeKey(scope))) {
                prefs.getInt(sizeKey(scope), DEFAULT_FONT_SIZE_PX)
            } else {
                null
            },
        )

        val raw = RawFontSettings(
            legacyFontName = legacyFont,
            legacySizePx = legacySizePx,
            reader = stored(FontScope.READER),
            ui = stored(FontScope.UI),
            editor = stored(FontScope.EDITOR),
        )

        // Version 2 introduced per-scope defaults. Upgrade values that were
        // never customised from the old single default (Bad Script/16).
        val migrated = if (
            prefs.getInt(KEY_FONT_SETTINGS_VERSION, 1) < CURRENT_FONT_SETTINGS_VERSION
        ) {
            FontSettings.migrateOldDefaults(raw)
        } else {
            raw
        }

        return FontSettings.resolve(migrated)
    }

    /**
     * Writes the resolved snapshot back. This performs the one-time
     * migration from the legacy single font/size to all three scopes,
     * applies the current defaults version and normalises out-of-range
     * values.
     */
    private fun persistResolved(
        prefs: android.content.SharedPreferences,
        resolved: FontSettings,
    ) {
        val editor = prefs.edit()
        var changed = false

        FontScope.entries.forEach { scope ->
            val scopeSettings = resolved[scope]
            if (prefs.getString(fontKey(scope), null) != scopeSettings.fontName) {
                editor.putString(fontKey(scope), scopeSettings.fontName)
                changed = true
            }
            if (prefs.getInt(sizeKey(scope), Int.MIN_VALUE) != scopeSettings.sizePx) {
                editor.putInt(sizeKey(scope), scopeSettings.sizePx)
                changed = true
            }
        }

        if (prefs.contains(KEY_LEGACY_FONT_SCALE)) {
            editor.remove(KEY_LEGACY_FONT_SCALE)
            changed = true
        }

        if (prefs.getInt(KEY_FONT_SETTINGS_VERSION, 0) != CURRENT_FONT_SETTINGS_VERSION) {
            editor.putInt(KEY_FONT_SETTINGS_VERSION, CURRENT_FONT_SETTINGS_VERSION)
            changed = true
        }

        if (changed) editor.apply()
    }
}
