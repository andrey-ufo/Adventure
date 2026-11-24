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

object FontManager {

    private const val PREFS_NAME = "font_prefs"
    private const val KEY_SELECTED_FONT = "selected_font"
    private const val KEY_CUSTOM_FONTS = "custom_fonts"
    private const val KEY_FONT_SIZE_PX = "font_size_px"
    private const val KEY_LEGACY_FONT_SCALE = "font_scale"
    private const val FONTS_DIR = "fonts"
    private const val DEFAULT_FONT = "Bad Script"

    /** Базовый размер текста: 10–30 SP, по умолчанию 16 (как раньше «Обычный»). */
    const val DEFAULT_FONT_SIZE_PX = 16
    const val MIN_FONT_SIZE_PX = 10
    const val MAX_FONT_SIZE_PX = 30

    /**
     * Текущий размер, выбранный ползунком. `null`, пока значение не задано
     * в этой сессии — тогда читаем из prefs. Поток позволяет обновлять
     * масштаб во всех открытых Compose-экранах без перезапуска активити.
     */
    private val _fontSizePx = MutableStateFlow<Int?>(null)
    val fontSizePx: StateFlow<Int?> = _fontSizePx.asStateFlow()

    fun getFontSizePx(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getInt(KEY_FONT_SIZE_PX, -1)
        if (stored in MIN_FONT_SIZE_PX..MAX_FONT_SIZE_PX) return stored
        return migrateLegacyScale(context, prefs) ?: DEFAULT_FONT_SIZE_PX
    }

    fun setFontSizePx(context: Context, sizePx: Int) {
        val clamped = sizePx.coerceIn(MIN_FONT_SIZE_PX, MAX_FONT_SIZE_PX)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_FONT_SIZE_PX, clamped).apply()
        _fontSizePx.value = clamped
        Logger.log(context, "Размер шрифта: $clamped px")
    }

    /** Множитель для совместимости с масштабированием текста в View-дереве. */
    fun getFontScale(context: Context): Float =
        getFontSizePx(context) / DEFAULT_FONT_SIZE_PX.toFloat()

    private fun migrateLegacyScale(
        context: Context,
        prefs: android.content.SharedPreferences,
    ): Int? {
        if (!prefs.contains(KEY_LEGACY_FONT_SCALE)) return null
        val scale = prefs.getFloat(KEY_LEGACY_FONT_SCALE, 1.0f)
        val migrated = (scale * DEFAULT_FONT_SIZE_PX).roundToInt()
            .coerceIn(MIN_FONT_SIZE_PX, MAX_FONT_SIZE_PX)
        prefs.edit()
            .putInt(KEY_FONT_SIZE_PX, migrated)
            .remove(KEY_LEGACY_FONT_SCALE)
            .apply()
        Logger.log(context, "Миграция размера шрифта: $scale → $migrated px")
        return migrated
    }

    fun getAvailableFonts(context: Context): List<String> {
        val fonts = mutableListOf(DEFAULT_FONT, "Caveat", "AmazHand_First", "EuroScript", "MarckScript")
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        fonts.addAll(prefs.getStringSet(KEY_CUSTOM_FONTS, emptySet()) ?: emptySet())
        return fonts
    }

    fun addFont(context: Context, sourceUri: Uri, fileName: String) {
        val fontsDir = File(context.filesDir, FONTS_DIR)
        if (!fontsDir.exists()) fontsDir.mkdirs()
        val destFile = File(fontsDir, fileName)
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            FileOutputStream(destFile).use { output -> input.copyTo(output) }
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val customFonts = prefs.getStringSet(KEY_CUSTOM_FONTS, emptySet())?.toMutableSet() ?: mutableSetOf()
        customFonts.add(fileName)
        prefs.edit().putStringSet(KEY_CUSTOM_FONTS, customFonts).apply()
        Logger.log(context, "Шрифт добавлен: $fileName")
    }

    fun getTypeface(context: Context, fontName: String): Typeface = when (fontName) {
        "По умолчанию", DEFAULT_FONT -> Typeface.createFromAsset(context.assets, "fonts/BadScript-Regular.ttf")
        "Caveat" -> Typeface.createFromAsset(context.assets, "fonts/Caveat-Regular.ttf")
        "AmazHand_First" -> Typeface.createFromAsset(context.assets, "fonts/AmazHand_First.ttf")
        "EuroScript" -> Typeface.createFromAsset(context.assets, "fonts/EuroScript.ttf")
        "MarckScript" -> Typeface.createFromAsset(context.assets, "fonts/MarckScript-Regular.ttf")

        else -> {
            val fontFile = File(context.filesDir, "$FONTS_DIR/$fontName")
            if (fontFile.exists()) Typeface.createFromFile(fontFile) else Typeface.DEFAULT
        }
    }

    fun setSelectedFont(context: Context, fontName: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_SELECTED_FONT, fontName).apply()
        Logger.log(context, "Выбран шрифт: $fontName")
    }

    fun getSelectedFont(context: Context): String {
        val stored = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SELECTED_FONT, DEFAULT_FONT)
        // "По умолчанию" раньше означал системный шрифт; теперь шрифт по умолчанию — Bad Script.
        if (stored == null || stored == "По умолчанию") return DEFAULT_FONT
        return stored
    }

    /**
     * Returns the user-selected font as a Compose [FontFamily].
     * - default → [FontFamily.Default] (system font, respects user's
     *   system font settings).
     * - bundled / custom .ttf → wrapped via [FontFamily] over the same
     *   Typeface that [getTypeface] resolves.
     *
     * Font *size* scaling is applied separately in `AdventureTheme` via
     * `LocalDensity.fontScale`; this method concerns only the typeface.
     */
    fun getFontFamily(context: Context): FontFamily {
        val selected = getSelectedFont(context)
        val typeface = getTypeface(context, selected)
        if (typeface == Typeface.DEFAULT) return FontFamily.Default
        return FontFamily(typeface)
    }
}