package com.myapp.adventure.data

import java.util.UUID

data class EditorScene(
    val uid: String = UUID.randomUUID().toString(),
    var id: String = "",
    var idIsAuto: Boolean = true,
    var title: String = "",
    var text: String = "",
    var isChapterEnd: Boolean = false,
    var requiredSteps: Int = 0,
    var isDeathScene: Boolean = false,
    var deathAllowStepBack: Boolean = true,
    var deathAllowChapterRestart: Boolean = true,
    var deathAllowAdventureRestart: Boolean = true,
    val tags: MutableList<EditorTag> = mutableListOf(),
    val choices: MutableList<EditorChoice> = mutableListOf()
)

data class EditorChoice(
    val uid: String = UUID.randomUUID().toString(),
    var text: String = "",
    var nextSceneId: String = "",
    var conditionTag: String = "",
    val tags: MutableList<EditorTag> = mutableListOf()
)

data class EditorTag(
    var name: String = "",
    var value: Int = 0,
    var isStatModifier: Boolean = false,
    var category: TagCategory = TagCategory.JOURNAL,
    var displayName: String = "",
    var visible: Boolean = false
)

fun EditorScene.deepCopy(): EditorScene = copy(
    tags = tags.map { it.copy() }.toMutableList(),
    choices = choices.map { choice ->
        choice.copy(tags = choice.tags.map { it.copy() }.toMutableList())
    }.toMutableList()
)

data class RebuildResult(
    val oldId: String,
    val newId: String,
    val updatedReferences: Int
)