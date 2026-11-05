package com.myapp.adventure.data

data class PlayerStateSnapshot(
    val sceneId: String = "",
    val chapterIndex: Int = 0,
    val flags: List<String> = emptyList(),
    val stats: Map<String, Int> = emptyMap(),
    val itemOrder: List<String> = emptyList(),
    val appliedSceneEffects: Set<String> = emptySet()
)