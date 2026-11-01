package com.myapp.adventure.data

data class StoryStats(
    val totalScenes: Int,
    val totalWords: Int,
    val totalChoices: Int,
    val totalChapters: Int,
    val estimatedReadingMinutes: Int,
    val avgWordsPerScene: Int,
    val longestSceneTitle: String,
    val longestSceneWords: Int,
    val shortestSceneTitle: String,
    val shortestSceneWords: Int,
    val chapters: List<ChapterStats>
)

data class ChapterStats(
    val index: Int,
    val sceneCount: Int,
    val wordCount: Int,
    val choiceCount: Int,
    val requiredSteps: Int,
    val isLast: Boolean
)