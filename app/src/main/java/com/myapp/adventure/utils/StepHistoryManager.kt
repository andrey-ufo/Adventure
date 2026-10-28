package com.myapp.adventure.utils

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

data class DaySteps(
    val date: String,
    val dayOfWeek: String,
    val steps: Int
)

data class WeekData(
    val label: String,
    val totalSteps: Int,
    val days: List<DaySteps>
)

object StepHistoryManager {

    private val gson = Gson()
    private const val HISTORY_FILE_NAME = "step_history.json"
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun getHistory(context: Context): List<Pair<String, Int>> {
        return loadHistory(context)
    }

    fun recordCurrentSteps(context: Context, absoluteSteps: Int) {
        val today = LocalDate.now().format(dateFormatter)
        val history = loadHistory(context).toMutableList()

        if (history.isNotEmpty() && history.last().first == today) {
            val lastSteps = history.last().second
            if (absoluteSteps > lastSteps) {
                history[history.size - 1] = today to absoluteSteps
                saveHistory(context, history)
            }
        } else {
            history.add(today to absoluteSteps)
            val cutoff = LocalDate.now().minusDays(14).format(dateFormatter)
            val trimmed = history.filter { it.first >= cutoff }
            saveHistory(context, trimmed)
        }
    }

    fun getWeeksDetailed(context: Context): List<WeekData> {
        val history = loadHistory(context)
        val today = LocalDate.now()
        val currentMonday = today.with(DayOfWeek.MONDAY)
        val lastMonday = currentMonday.minusWeeks(1)
        val lastSunday = currentMonday.minusDays(1)

        fun getDaysOfWeek(weekLabel: String, startDate: LocalDate, endDate: LocalDate): WeekData {
            var total = 0
            val days = mutableListOf<DaySteps>()
            var previousAbsolute: Int? = null

            val dayBefore = startDate.minusDays(1)
            val dayBeforeStr = dayBefore.format(dateFormatter)
            previousAbsolute = history.find { it.first == dayBeforeStr }?.second

            var currentDate = startDate
            while (!currentDate.isAfter(endDate)) {
                val dateStr = currentDate.format(dateFormatter)
                val dayOfWeek = currentDate.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.forLanguageTag("ru"))
                val absoluteAtEnd = history.find { it.first == dateStr }?.second
                val dailySteps = if (absoluteAtEnd != null) {
                    if (previousAbsolute != null) (absoluteAtEnd - previousAbsolute).coerceAtLeast(0)
                    else absoluteAtEnd
                } else {
                    0
                }
                total += dailySteps
                days.add(DaySteps(dateStr, dayOfWeek, dailySteps))
                previousAbsolute = absoluteAtEnd
                currentDate = currentDate.plusDays(1)
            }
            return WeekData(weekLabel, total, days)
        }

        return listOf(
            getDaysOfWeek("Прошлая неделя", lastMonday, lastSunday),
            getDaysOfWeek("Текущая неделя", currentMonday, today)
        )
    }

    fun getDailySteps(context: Context, days: Int = 14): List<Pair<String, Int>> {
        val history = loadHistory(context)
        val today = LocalDate.now()
        val result = mutableListOf<Pair<String, Int>>()
        for (i in days - 1 downTo 0) {
            val date = today.minusDays(i.toLong()).format(dateFormatter)
            val currentAbs = history.find { it.first == date }?.second
            val prevDate = today.minusDays((i + 1).toLong()).format(dateFormatter)
            val prevAbs = history.find { it.first == prevDate }?.second
            val dailySteps = if (currentAbs != null && prevAbs != null) {
                (currentAbs - prevAbs).coerceAtLeast(0)
            } else {
                currentAbs ?: 0
            }
            result.add(date to dailySteps)
        }
        return result
    }

    fun getWeeklySteps(context: Context): List<Pair<String, Int>> {
        val daily = getDailySteps(context, 14)
        val today = LocalDate.now()
        val currentWeekMonday = today.with(DayOfWeek.MONDAY)
        val lastWeekMonday = currentWeekMonday.minusWeeks(1)

        var currentWeekSum = 0
        var lastWeekSum = 0

        for ((dateStr, steps) in daily) {
            val date = LocalDate.parse(dateStr, dateFormatter)
            if (!date.isBefore(currentWeekMonday) && !date.isAfter(today)) {
                currentWeekSum += steps
            } else if (!date.isBefore(lastWeekMonday) && date.isBefore(currentWeekMonday)) {
                lastWeekSum += steps
            }
        }
        return listOf(
            "Эта неделя" to currentWeekSum,
            "Прошлая неделя" to lastWeekSum
        )
    }

    private fun loadHistory(context: Context): List<Pair<String, Int>> {
        val fileUri = getHistoryFileUri(context) ?: return emptyList()
        return try {
            val json = context.contentResolver.openInputStream(fileUri)?.bufferedReader()?.use { it.readText() }
            if (json.isNullOrBlank()) emptyList()
            else {
                val type = object : TypeToken<List<List<Any>>>() {}.type
                val raw = gson.fromJson<List<List<Any>>>(json, type)
                raw.mapNotNull { entry ->
                    val date = entry.getOrNull(0) as? String
                    val steps = (entry.getOrNull(1) as? Double)?.toInt()
                    if (date != null && steps != null) date to steps else null
                }
            }
        } catch (e: Exception) {
            Logger.log(context, "Ошибка загрузки истории шагов: ${e.message}")
            emptyList()
        }
    }

    private fun saveHistory(context: Context, history: List<Pair<String, Int>>) {
        val fileUri = getOrCreateHistoryFile(context) ?: return
        try {
            val json = gson.toJson(history.map { listOf(it.first, it.second) })
            context.contentResolver.openOutputStream(fileUri, "wt")?.use { outputStream ->
                outputStream.write(json.toByteArray())
            }
            Logger.log(context, "История шагов сохранена, записей: ${history.size}")
        } catch (e: Exception) {
            Logger.log(context, "Ошибка сохранения истории шагов: ${e.message}")
        }
    }

    private fun getHistoryFileUri(context: Context): Uri? {
        val folderUri = getBaseFolderUri(context) ?: return null
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            folderUri,
            DocumentsContract.getTreeDocumentId(folderUri)
        )
        val cursor = context.contentResolver.query(childrenUri, null, null, null, null)
        cursor?.use {
            while (it.moveToNext()) {
                val name = it.getString(it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME))
                if (name == HISTORY_FILE_NAME) {
                    val docId = it.getString(it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID))
                    return DocumentsContract.buildDocumentUriUsingTree(folderUri, docId)
                }
            }
        }
        return null
    }

    private fun getOrCreateHistoryFile(context: Context): Uri? {
        val folderUri = getBaseFolderUri(context) ?: return null
        val existing = getHistoryFileUri(context)
        if (existing != null) return existing

        val parentDocId = DocumentsContract.getTreeDocumentId(folderUri)
        val rootDocUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, parentDocId)
        return try {
            DocumentsContract.createDocument(
                context.contentResolver,
                rootDocUri,
                "application/json",
                HISTORY_FILE_NAME
            )
        } catch (e: Exception) {
            Logger.log(context, "Ошибка создания step_history.json: ${e.message}")
            null
        }
    }

    private fun getBaseFolderUri(context: Context): Uri? {
        val prefs = context.getSharedPreferences("adventure_prefs", Context.MODE_PRIVATE)
        val uriString = prefs.getString("adventure_folder_uri", null) ?: return null
        return Uri.parse(uriString)
    }
}