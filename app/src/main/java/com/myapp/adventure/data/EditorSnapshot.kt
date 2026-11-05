package com.myapp.adventure.data

data class EditorSnapshot(
    val title: String = "",
    val author: String = "",
    val description: String = "",
    val initialStateTags: List<EditorTag> = emptyList(),
    val scenes: List<EditorScene> = emptyList()
)