package com.myapp.adventure.data

data class EditorScene(
    var id: String = "",            // теперь можно изменять
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