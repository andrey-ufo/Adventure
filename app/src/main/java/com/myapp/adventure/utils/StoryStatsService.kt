package com.myapp.adventure.utils

import com.myapp.adventure.data.ChapterStats
import com.myapp.adventure.data.EditorScene
import com.myapp.adventure.data.StoryStats
import kotlin.math.roundToInt

object StoryStatsService {

    /** Средняя скорость чтения, слов в минуту. */
    private const val WORDS_PER_MINUTE = 200.0

    /** Дополнительное время на обдумывание одного выбора, секунд. */
    private const val SECONDS_PER_CHOICE = 20

    fun calculate(scenes: List<EditorScene>): StoryStats {
        if (scenes.isEmpty()) {
            return StoryStats(
                totalScenes = 0,
                totalWords = 0,
                totalChoices = 0,
                totalChapters = 0,
                estimatedReadingMinutes = 0,
                avgWordsPerScene = 0,
                longestSceneTitle = "",
                longestSceneWords = 0,
                shortestSceneTitle = "",
                shortestSceneWords = 0,
                chapters = emptyList()
            )
        }

        val perSceneWords = scenes.map { countWords(it.text) }
        val totalWords = perSceneWords.sum()
        val totalChoices = scenes.sumOf { it.choices.size }

        // Самая длинная и самая короткая сцены
        var longestIdx = 0
        var shortestIdx = 0
        for (i in perSceneWords.indices) {
            if (perSceneWords[i] > perSceneWords[longestIdx]) longestIdx = i
            if (perSceneWords[i] < perSceneWords[shortestIdx]) shortestIdx = i
        }

        // Разбивка по главам — тот же алгоритм, что в buildAdventure()
        val chapters = buildChapterGroups(scenes)
        val chapterStats = chapters.mapIndexed { idx, group ->
            val isLast = idx == chapters.size - 1
            val wordCount = group.sumOf { countWords(it.text) }
            val choiceCount = group.sumOf { it.choices.size }
            val requiredSteps = if (!isLast) group.last().requiredSteps else 0
            ChapterStats(
                index = idx + 1,
                sceneCount = group.size,
                wordCount = wordCount,
                choiceCount = choiceCount,
                requiredSteps = requiredSteps,
                isLast = isLast
            )
        }

        // Время чтения: слова + обдумывание выборов
        val readingSeconds = (totalWords / WORDS_PER_MINUTE * 60.0).roundToInt() +
                totalChoices * SECONDS_PER_CHOICE
        val readingMinutes = (readingSeconds / 60.0).roundToInt()

        val avgWords = (totalWords.toDouble() / scenes.size).roundToInt()

        return StoryStats(
            totalScenes = scenes.size,
            totalWords = totalWords,
            totalChoices = totalChoices,
            totalChapters = chapters.size,
            estimatedReadingMinutes = readingMinutes,
            avgWordsPerScene = avgWords,
            longestSceneTitle = scenes[longestIdx].title.ifBlank { "(без названия)" },
            longestSceneWords = perSceneWords[longestIdx],
            shortestSceneTitle = scenes[shortestIdx].title.ifBlank { "(без названия)" },
            shortestSceneWords = perSceneWords[shortestIdx],
            chapters = chapterStats
        )
    }

    /**
     * Считает слова: разбивает по пробельным символам, отбрасывает пустые.
     * Подходит для русского и большинства европейских языков.
     */
    private fun countWords(text: String): Int {
        if (text.isBlank()) return 0
        return text.split(Regex("\\s+")).count { it.isNotBlank() }
    }

    private fun buildChapterGroups(scenes: List<EditorScene>): List<List<EditorScene>> {
        val groups = mutableListOf<List<EditorScene>>()
        var current = mutableListOf<EditorScene>()
        for (scene in scenes) {
            current.add(scene)
            if (scene.isChapterEnd || scene == scenes.last()) {
                groups.add(current.toList())
                current = mutableListOf()
            }
        }
        if (current.isNotEmpty()) groups.add(current)
        return groups
    }
}