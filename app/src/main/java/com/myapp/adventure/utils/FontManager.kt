package com.myapp.adventure.utils

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.myapp.adventure.R
import java.io.File
import java.io.FileOutputStream

object FontManager {

    private const val PREFS_NAME = "font_prefs"
    private const val KEY_SELECTED_FONT = "selected_font"
    private const val KEY_CUSTOM_FONTS = "custom_fonts"
    private const val KEY_FONT_SCALE = "font_scale"
    private const val FONTS_DIR = "fonts"

    /** Доступные масштабы: отображаемое имя → множитель. */
    val FONT_SCALES: List<Pair<String, Float>> = listOf(
        "Мелкий"   to 0.85f,
        "Обычный"  to 1.0f,
        "Крупный"  to 1.15f,
        "Очень крупный" to 1.3f
    )

    fun getFontScale(context: Context): Float {
        val scale = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getFloat(KEY_FONT_SCALE, 1.0f)
        return FONT_SCALES.firstOrNull { it.second == scale }?.second
            ?: FONT_SCALES.minByOrNull { kotlin.math.abs(it.second - scale) }!!.second
    }

    fun setFontScale(context: Context, scale: Float) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putFloat(KEY_FONT_SCALE, scale).apply()
        Logger.log(context, "Масштаб шрифта: $scale")
    }

    fun getAvailableFonts(context: Context): List<String> {
        val fonts = mutableListOf("По умолчанию", "Bad Script", "Caveat")
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
        "По умолчанию" -> Typeface.DEFAULT
        "Bad Script" -> Typeface.createFromAsset(context.assets, "fonts/BadScript-Regular.ttf")
        "Caveat" -> Typeface.createFromAsset(context.assets, "fonts/Caveat-Regular.ttf")
        else -> {
            val fontFile = File(context.filesDir, "$FONTS_DIR/$fontName")
            if (fontFile.exists()) Typeface.createFromFile(fontFile) else Typeface.DEFAULT
        }
    }

    /** Применяет шрифт и текущий масштаб ко всему дереву View. */
    fun applyFontToViewTree(view: View, fontName: String, context: Context) {
        val scale = getFontScale(context)
        applyRecursive(view, fontName, scale, context)
    }

    private fun applyRecursive(view: View, fontName: String, scale: Float, context: Context) {
        if (view is TextView) {
            view.typeface = getTypeface(context, fontName)
            applyScaleToTextView(view, scale)
        } else if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                applyRecursive(view.getChildAt(i), fontName, scale, context)
            }
        }
    }

    /**
     * Масштабирует размер текста относительно исходного, сохранённого в теге.
     * Текст задаётся в SP, поэтому соблюдаются системные настройки доступности.
     */
    private fun applyScaleToTextView(tv: TextView, scale: Float) {
        // Исходный размер храним в SP, а не в PX, чтобы не зависеть от плотности экрана
        val originalSp = (tv.getTag(R.id.original_text_size) as? Float)
            ?: tv.textSize.let { px ->
                val sp = px / tv.resources.displayMetrics.scaledDensity
                tv.setTag(R.id.original_text_size, sp)
                sp
            }
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, originalSp * scale)
    }

    fun setSelectedFont(context: Context, fontName: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_SELECTED_FONT, fontName).apply()
        Logger.log(context, "Выбран шрифт: $fontName")
    }

    fun getSelectedFont(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SELECTED_FONT, "По умолчанию") ?: "По умолчанию"
    }
}