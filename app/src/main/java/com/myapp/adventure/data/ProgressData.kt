package com.myapp.adventure.data

data class ProgressData(
    val adventureFileName: String,
    val currentChapterIndex: Int = 0,
    val currentSceneId: String = "",
    val flags: Set<String> = emptySet(),
    val stats: Map<String, Int> = emptyMap(),
    val currentStepsDone: Int = 0,
    val appliedSceneEffects: Set<String> = emptySet(),
    val goalAbsoluteSteps: Int = -1,      // -1 означает, что цель не установлена
    val startAbsoluteSteps: Int = 0       // абсолютное значение при старте цели
)