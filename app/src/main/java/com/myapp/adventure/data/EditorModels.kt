package com.myapp.adventure.data

import java.util.UUID

data class EditorScene(
    val uid: String = UUID.randomUUID().toString(),
    var id: String = "",
    var title: String = "",
    var text: String = "",
    var isChapterEnd: Boolean = false,
    var requiredSteps: Int = 0,
    val tags: MutableList<EditorTag> = mutableListOf(),
    val choices: MutableList<EditorChoice> = mutableListOf()
)

data class EditorChoice(
    var text: String = "",
    var nextSceneId: String = "",
    var conditionTag: String = ""
)

data class EditorTag(
    var name: String = "",
    var value: Int = 0,
    var isStatModifier: Boolean = false
)

fun EditorScene.deepCopy(): EditorScene = copy(
    tags = tags.map { it.copy() }.toMutableList(),
    choices = choices.map { it.copy() }.toMutableList()
)