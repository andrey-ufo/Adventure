package com.myapp.adventure.utils

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import java.io.File
import java.io.FileOutputStream

object FontManager {

    private const val PREFS_NAME = "font_prefs"
    private const val KEY_SELECTED_FONT = "selected_font"
    private const val KEY_CUSTOM_FONTS = "custom_fonts"
    private const val FONTS_DIR = "fonts"

    fun getAvailableFonts(context: Context): List<String> {
        val fonts = mutableListOf("По умолчанию")
        fonts.add("Bad Script")
        fonts.add("Caveat")
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val customFonts = prefs.getStringSet(KEY_CUSTOM_FONTS, emptySet()) ?: emptySet()
        fonts.addAll(customFonts)
        return fonts
    }

    fun addFont(context: Context, sourceUri: Uri, fileName: String) {
        val fontsDir = File(context.filesDir, FONTS_DIR)
        if (!fontsDir.exists()) fontsDir.mkdirs()
        val destFile = File(fontsDir, fileName)
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val customFonts = prefs.getStringSet(KEY_CUSTOM_FONTS, emptySet())?.toMutableSet() ?: mutableSetOf()
        customFonts.add(fileName)
        prefs.edit().putStringSet(KEY_CUSTOM_FONTS, customFonts).apply()
        Logger.log(context, "Шрифт добавлен: $fileName")
    }

    fun getTypeface(context: Context, fontName: String): Typeface {
        return when (fontName) {
            "По умолчанию" -> Typeface.DEFAULT
            "Bad Script" -> Typeface.createFromAsset(context.assets, "fonts/BadScript-Regular.ttf")
            "Caveat" -> Typeface.createFromAsset(context.assets, "fonts/Caveat-Regular.ttf")
            else -> {
                val fontFile = File(context.filesDir, "$FONTS_DIR/$fontName")
                if (fontFile.exists()) Typeface.createFromFile(fontFile) else Typeface.DEFAULT
            }
        }
    }

    /**
     * Рекурсивно применяет шрифт ко всем TextView внутри указанного view.
     */
    fun applyFontToViewTree(view: View, fontName: String, context: Context) {
        if (view is TextView) {
            view.typeface = getTypeface(context, fontName)
        } else if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                applyFontToViewTree(view.getChildAt(i), fontName, context)
            }
        }
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